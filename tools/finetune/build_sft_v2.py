"""v2 SFT files: B's synthetic gold plus partial-utterance rows and witness-presence rows (the user's approval of
missing-mandatory training rows is the reason this script exists; B's v1 rows all answered all four mandatory questions
and the model learned to always emit severity, equipment and witnesses).

Two derivations, both exact (the label changes with the words, nothing is guessed):

* drop rows: from an initial-report row (no saved context) in a sentence-punctuated style, remove one or two of the
  sentences that carry exactly one optional or mandatory field (severity, equipment, witnesses, actions, time), and
  remove that field and its evidence from the target. The model must learn to omit what was not said.
* presence rows: a witness sentence "X saw the incident." becomes "My friend X was with me when it happened." (or
  "X was there." / "X was standing next to me."): same field, evidence rewritten to the new quote.

    python tools/finetune/build_sft_v2.py --gold-dir <v1 dir> --prompt <extract_v11.txt|extract_v12.txt> --out <dir> \
        [--drop-ids audit.json] [--strip-location-prefix] [--seed 42]

Writes sft_train.jsonl, sft_val.jsonl (evaluation of loss only), sft_stats.json. The test split is not touched or used.
"""
import argparse
import json
import random
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from build_sft import compact, strip_location  # noqa: E402

SENT = re.compile(r"(?<=[.!?])\s+")
DROPPABLE = ["severity", "equipment_asset", "witnesses", "immediate_actions", "date_time_text"]
PRESENCE = [
    "My friend {n} was with me when it happened.",
    "{n} was there when it happened.",
    "{n} was standing next to me when it happened.",
    "{n} was with me at the time.",
    "My colleague {n} was nearby when it happened.",
]


def load(p):
    return [json.loads(l) for l in Path(p).read_text(encoding="utf-8").splitlines() if l.strip()]


def user_message(raw):
    return f'Report so far: {{}}\nWorker: "{raw}"'


def make(row, raw, args, evidence, system):
    a = {k: v for k, v in args.items() if k != "evidence"}
    a["evidence"] = evidence
    answer = compact({"tool": "update_report", "arguments": a})
    return {"messages": [{"role": "system", "content": system}, {"role": "user", "content": user_message(raw)},
                         {"role": "assistant", "content": answer}]}


def eligible(row):
    return (row["split"] in ("train", "validation") and not row["report_before"] and not row.get("earlier_worker_texts")
            and row["style"] in ("direct", "conversational", "vague") and "update_sequence" not in row.get("tags", []))


def sentence_of(sentences, quote):
    hits = [i for i, s in enumerate(sentences) if quote and quote in s]
    return hits[0] if len(hits) == 1 else None


def drops(row, rnd, stats):
    """Yield (raw, arguments, evidence) variants with fields removed together with the sentence that carried them."""
    args = json.loads(row["messages"][2]["content"])["arguments"]
    ev = dict(args.get("evidence", {}))
    sents = SENT.split(row["raw_text"].strip())
    owner = {}
    for field, quote in ev.items():
        i = sentence_of(sents, quote)
        if i is not None:
            owner.setdefault(i, []).append(field)
    cand = [f for f in DROPPABLE if f in args and f in ev]
    ok = []
    for f in cand:
        i = sentence_of(sents, ev[f])
        if i is None:
            continue
        # the sentence must carry only this field, and other fields' quotes must not live inside it
        if owner.get(i) == [f] and len(sents) > 2:
            ok.append((f, i))
    if not ok:
        return
    for _ in range(2):
        k = min(len(ok), rnd.choice([1, 1, 2]))
        chosen = rnd.sample(ok, k)
        if len({i for _, i in chosen}) != len(chosen):
            continue
        gone = {i for _, i in chosen}
        raw = " ".join(s for j, s in enumerate(sents) if j not in gone)
        a2 = {k_: v for k_, v in args.items() if k_ not in {f for f, _ in chosen} and k_ != "evidence"}
        e2 = {k_: v for k_, v in ev.items() if k_ not in {f for f, _ in chosen}}
        # the mandatory fields that remain must still be quoted in the remaining words
        if any(q not in raw for q in e2.values()):
            continue
        stats["drop_" + "+".join(sorted(f for f, _ in chosen))] = stats.get("drop_" + "+".join(sorted(f for f, _ in chosen)), 0) + 1
        yield raw, a2, e2


def presence(row, rnd, stats):
    args = json.loads(row["messages"][2]["content"])["arguments"]
    ev = args.get("evidence", {})
    names = args.get("witnesses") or []
    quote = ev.get("witnesses")
    if len(names) != 1 or not quote or quote not in row["raw_text"]:
        return
    if "persons_involved" in args and names[0] in args["persons_involved"]:
        return
    sentence = rnd.choice(PRESENCE).format(n=names[0])
    # replace the exact sentence that holds the quote (it must be the whole sentence, else skip)
    sents = SENT.split(row["raw_text"].strip())
    i = sentence_of(sents, quote)
    if i is None or sents[i].rstrip(".!? ") != quote.rstrip(".!? "):
        return
    sents[i] = sentence
    raw = " ".join(sents)
    e2 = dict(ev)
    e2["witnesses"] = sentence.rstrip(".")
    stats["presence"] = stats.get("presence", 0) + 1
    yield raw, {k: v for k, v in args.items() if k != "evidence"}, e2


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gold-dir", required=True)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--drop-ids")
    ap.add_argument("--strip-location-prefix", action="store_true")
    ap.add_argument("--seed", type=int, default=42)
    a = ap.parse_args()

    system = Path(a.prompt).read_text(encoding="utf-8").strip()
    drop_ids = set(json.loads(Path(a.drop_ids).read_text(encoding="utf-8"))["issues"]) if a.drop_ids else set()
    rnd = random.Random(a.seed)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    files = {"train": open(out / "sft_train.jsonl", "w", encoding="utf-8", newline="\n"),
             "validation": open(out / "sft_val.jsonl", "w", encoding="utf-8", newline="\n")}
    stats = {"original": {"train": 0, "validation": 0}, "added": {"train": 0, "validation": 0}}
    for row in load(Path(a.gold_dir) / "gold.jsonl"):
        split = row["split"]
        if split not in files or row["id"] in drop_ids:
            continue
        if a.strip_location_prefix:
            row, _ = strip_location(row)
        m = row["messages"]
        files[split].write(json.dumps({"messages": [{"role": "system", "content": system}, m[1], m[2]]}, ensure_ascii=False) + "\n")
        stats["original"][split] += 1
        if not eligible(row):
            continue
        for raw, args, ev in list(drops(row, rnd, stats)) + list(presence(row, rnd, stats)):
            files[split].write(json.dumps(make(row, raw, args, ev, system), ensure_ascii=False) + "\n")
            stats["added"][split] += 1
    for f in files.values():
        f.close()
    (out / "sft_stats.json").write_text(json.dumps(stats, indent=2), encoding="utf-8")
    print(json.dumps(stats, indent=2))


if __name__ == "__main__":
    main()
