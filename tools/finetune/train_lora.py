"""LoRA fine-tune of Qwen3-1.7B on the SFT files from build_sft.py. Plain PyTorch + peft (no trl, no Trainer), so it
does not depend on a particular transformers release.

    python train_lora.py --data <dir with sft_train.jsonl, sft_val.jsonl> --out <run dir> [--base Qwen/Qwen3-1.7B]

Loss is on the assistant answer and its <|im_end|> only. Prompts are rendered with the Qwen3 chat template, thinking
off (an empty think block before the answer), which is how the on-device engine renders them.
Writes <out>/adapter_epoch{N}/, <out>/adapter_best/, <out>/merged/ (bf16, plain HF folder: the input of the Qualcomm
quantize step) and <out>/train_log.json.
"""
import argparse
import json
import math
import random
import time
from pathlib import Path

import torch
from peft import LoraConfig, PeftModel, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer

ARGS = argparse.ArgumentParser()
ARGS.add_argument("--data", required=True)
ARGS.add_argument("--out", required=True)
ARGS.add_argument("--base", default="Qwen/Qwen3-1.7B")
ARGS.add_argument("--epochs", type=int, default=3)
ARGS.add_argument("--lr", type=float, default=1.5e-4)
ARGS.add_argument("--rank", type=int, default=32)
ARGS.add_argument("--micro-batch", type=int, default=4)
ARGS.add_argument("--accum", type=int, default=4)
ARGS.add_argument("--max-len", type=int, default=2048)
ARGS.add_argument("--seed", type=int, default=42)
ARGS.add_argument("--no-merge", action="store_true")


def load(p):
    return [json.loads(l) for l in Path(p).read_text(encoding="utf-8").splitlines() if l.strip()]


def encode(tok, ex, max_len):
    system, user, assistant = (m["content"] for m in ex["messages"])
    prompt = tok.apply_chat_template(
        [{"role": "system", "content": system}, {"role": "user", "content": user}],
        tokenize=False, add_generation_prompt=True, enable_thinking=False,
    )
    p = tok(prompt, add_special_tokens=False)["input_ids"]
    a = tok(assistant + "<|im_end|>", add_special_tokens=False)["input_ids"]
    ids = (p + a)[:max_len]
    labels = ([-100] * len(p) + a)[:max_len]
    return ids, labels


def batches(items, size, shuffle, rnd):
    order = list(range(len(items)))
    if shuffle:
        rnd.shuffle(order)
    for i in range(0, len(order), size):
        yield [items[j] for j in order[i:i + size]]


def collate(batch, pad_id, device):
    n = max(len(i) for i, _ in batch)
    ids = torch.full((len(batch), n), pad_id, dtype=torch.long)
    lab = torch.full((len(batch), n), -100, dtype=torch.long)
    att = torch.zeros((len(batch), n), dtype=torch.long)
    for r, (i, l) in enumerate(batch):
        ids[r, :len(i)] = torch.tensor(i)
        lab[r, :len(l)] = torch.tensor(l)
        att[r, :len(i)] = 1
    return ids.to(device), lab.to(device), att.to(device)


@torch.no_grad()
def evaluate(model, data, pad_id, device, micro):
    model.eval()
    total, count = 0.0, 0
    for b in batches(data, micro, False, None):
        ids, lab, att = collate(b, pad_id, device)
        with torch.autocast("cuda", dtype=torch.bfloat16):
            out = model(input_ids=ids, attention_mask=att, labels=lab)
        n = int((lab[:, 1:] != -100).sum())
        total += float(out.loss) * n
        count += n
    model.train()
    return total / max(1, count)


def main():
    a = ARGS.parse_args()
    random.seed(a.seed)
    torch.manual_seed(a.seed)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    device = "cuda"

    tok = AutoTokenizer.from_pretrained(a.base)
    train = [encode(tok, e, a.max_len) for e in load(Path(a.data) / "sft_train.jsonl")]
    val = [encode(tok, e, a.max_len) for e in load(Path(a.data) / "sft_val.jsonl")]
    lens = sorted(len(i) for i, _ in train)
    print(f"train {len(train)} val {len(val)}  tokens/row median {lens[len(lens)//2]} max {lens[-1]}", flush=True)

    model = AutoModelForCausalLM.from_pretrained(a.base, dtype=torch.bfloat16).to(device)
    model.config.use_cache = False
    cfg = LoraConfig(
        r=a.rank, lora_alpha=2 * a.rank, lora_dropout=0.05, task_type="CAUSAL_LM",
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
    )
    model = get_peft_model(model, cfg)
    model.print_trainable_parameters()
    model.train()

    params = [p for p in model.parameters() if p.requires_grad]
    opt = torch.optim.AdamW(params, lr=a.lr, weight_decay=0.0, betas=(0.9, 0.999))
    steps_per_epoch = math.ceil(len(train) / (a.micro_batch * a.accum))
    total_steps = steps_per_epoch * a.epochs
    warm = max(1, int(0.05 * total_steps))

    def lr_at(step):
        if step < warm:
            return a.lr * (step + 1) / warm
        prog = (step - warm) / max(1, total_steps - warm)
        return a.lr * (0.1 + 0.9 * 0.5 * (1 + math.cos(math.pi * prog)))

    pad = tok.pad_token_id if tok.pad_token_id is not None else tok.eos_token_id
    rnd = random.Random(a.seed)
    log = {"args": vars(a), "epochs": [], "started": time.strftime("%Y-%m-%d %H:%M:%S")}
    best = (float("inf"), None)
    step = 0
    t0 = time.time()
    base_val = evaluate(model, val, pad, device, a.micro_batch)
    print(f"val loss before training: {base_val:.4f}", flush=True)
    log["val_loss_before"] = base_val

    for ep in range(1, a.epochs + 1):
        run, nrun = 0.0, 0
        micro = list(batches(train, a.micro_batch, True, rnd))
        for k in range(0, len(micro), a.accum):
            group = micro[k:k + a.accum]
            ntok = sum(int(sum(1 for x in l[1:] if x != -100)) for b in group for _, l in b)
            for b in group:
                ids, lab, att = collate(b, pad, device)
                with torch.autocast("cuda", dtype=torch.bfloat16):
                    logits = model(input_ids=ids, attention_mask=att).logits
                loss_sum = torch.nn.functional.cross_entropy(
                    logits[:, :-1].float().reshape(-1, logits.size(-1)), lab[:, 1:].reshape(-1),
                    ignore_index=-100, reduction="sum",
                )
                (loss_sum / max(1, ntok)).backward()
                run += float(loss_sum)
            nrun += ntok
            for g in opt.param_groups:
                g["lr"] = lr_at(step)
            torch.nn.utils.clip_grad_norm_(params, 1.0)
            opt.step()
            opt.zero_grad(set_to_none=True)
            step += 1
            if step % 10 == 0:
                print(f"ep {ep} step {step}/{total_steps} loss {run / max(1, nrun):.4f} lr {lr_at(step):.2e} {time.time() - t0:.0f}s", flush=True)
        vl = evaluate(model, val, pad, device, a.micro_batch)
        row = {"epoch": ep, "train_loss": run / max(1, nrun), "val_loss": vl, "elapsed_s": round(time.time() - t0)}
        log["epochs"].append(row)
        print(row, flush=True)
        model.save_pretrained(out / f"adapter_epoch{ep}")
        if vl < best[0]:
            best = (vl, ep)
            model.save_pretrained(out / "adapter_best")
    log["best_epoch"] = best[1]
    log["finished"] = time.strftime("%Y-%m-%d %H:%M:%S")
    (out / "train_log.json").write_text(json.dumps(log, indent=2), encoding="utf-8")

    if not a.no_merge:
        del model, opt
        torch.cuda.empty_cache()
        base = AutoModelForCausalLM.from_pretrained(a.base, dtype=torch.bfloat16)
        merged = PeftModel.from_pretrained(base, str(out / "adapter_best")).merge_and_unload()
        merged.save_pretrained(out / "merged", safe_serialization=True)
        tok.save_pretrained(out / "merged")
        print("merged ->", out / "merged", flush=True)


if __name__ == "__main__":
    main()
