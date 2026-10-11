"""Greedy generation on B's held-out-from-training synthetic rows (sft_test.jsonl or sft_val.jsonl): one call per row, the
row's own system and user message, thinking off. Writes <out>/test_outputs.jsonl with the model's raw reply per row.

    python gen_test.py --model <HF id or merged dir> --data sft_test.jsonl --out <dir>

Scoring is local (tools/finetune/score_test.py): the replies are compared with the row's target patch.
"""
import argparse
import json
import time
from pathlib import Path

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--max-new", type=int, default=320)
    a = ap.parse_args()

    tok = AutoTokenizer.from_pretrained(a.model)
    model = AutoModelForCausalLM.from_pretrained(a.model, dtype=torch.bfloat16).to("cuda").eval()
    rows = [json.loads(l) for l in Path(a.data).read_text(encoding="utf-8").splitlines() if l.strip()]
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    res = []
    for i, r in enumerate(rows):
        system, user, _ = (m["content"] for m in r["messages"])
        prompt = tok.apply_chat_template(
            [{"role": "system", "content": system}, {"role": "user", "content": user}],
            tokenize=False, add_generation_prompt=True, enable_thinking=False,
        )
        enc = tok(prompt, return_tensors="pt", add_special_tokens=False).to("cuda")
        t0 = time.time()
        with torch.no_grad():
            gen = model.generate(**enc, max_new_tokens=a.max_new, do_sample=False, pad_token_id=tok.eos_token_id)
        text = tok.decode(gen[0, enc["input_ids"].shape[1]:], skip_special_tokens=True).strip()
        res.append({"row": i, "output": text, "completion_tokens": int(gen.shape[1] - enc["input_ids"].shape[1]),
                    "ms": round((time.time() - t0) * 1000)})
    (out / "test_outputs.jsonl").write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in res) + "\n", encoding="utf-8")
    print(f"{len(res)} rows -> {out}")


if __name__ == "__main__":
    main()
