"""Field-level score of model replies against B's target patches (synthetic, in-distribution: NOT a headline number).

    python score_test.py --data sft_test.jsonl --outputs <test_outputs.jsonl> [--train sft_train.jsonl]

Per field: correct (same value after normalisation), wrong, miss (expected, absent), hallucination (emitted, not expected).
With --train, also reports the subset whose worker words have 3-word-shingle Jaccard < 0.6 against every train row (A/026).
"""
import argparse
import json
import re
from pathlib import Path

FIELDS = ["incident_type", "severity", "location", "date_time_text", "persons_involved", "equipment_asset",
          "immediate_actions", "witnesses"]
STOP = {"the", "a", "an", "of", "in", "at", "near", "around", "to", "on", "is", "was", "and"}


def toks(s):
    return re.sub(r"[^\w\s]", " ", s.lower()).split()


def content(s):
    t = [w for w in toks(s) if w not in STOP]
    return set(t) or set(toks(s))


def same(field, a, b):
    if field in ("incident_type", "severity"):
        return str(a).strip().lower() == str(b).strip().lower()
    if field in ("persons_involved", "witnesses"):
        norm = lambda v: {" ".join(sorted(content(x))) for x in (v if isinstance(v, list) else [v])}
        return norm(a) == norm(b)
    ca, cb = content(str(a)), content(str(b))
    return bool(ca and cb) and 2 * len(ca & cb) / (len(ca) + len(cb)) >= 0.8


def args_of(text):
    try:
        o = json.loads(text[text.index("{"):text.rindex("}") + 1])
        a = o.get("arguments", {})
        return {k: v for k, v in a.items() if k != "evidence" and v not in (None, "", [])}, True
    except Exception:
        return {}, False


def worker_words(user):
    m = re.search(r'^Worker: "(.*)"$', user, re.MULTILINE | re.DOTALL)
    return m.group(1) if m else user


def shingles(s):
    t = toks(s)
    return {" ".join(t[i:i + 3]) for i in range(max(1, len(t) - 2))}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--outputs", required=True)
    ap.add_argument("--train")
    a = ap.parse_args()
    rows = [json.loads(l) for l in Path(a.data).read_text(encoding="utf-8").splitlines() if l.strip()]
    outs = [json.loads(l) for l in Path(a.outputs).read_text(encoding="utf-8").splitlines() if l.strip()]
    train = []
    if a.train:
        for l in Path(a.train).read_text(encoding="utf-8").splitlines():
            if l.strip():
                train.append(shingles(worker_words(json.loads(l)["messages"][1]["content"])))
    far = set()
    for i, r in enumerate(rows):
        s = shingles(worker_words(r["messages"][1]["content"]))
        best = max((len(s & t) / max(1, len(s | t)) for t in train), default=0.0)
        if best < 0.6:
            far.add(i)

    def run(idx):
        c = dict(correct=0, wrong=0, miss=0, halluc=0, empty=0)
        exact = valid = 0
        for i in idx:
            want, _ = args_of(rows[i]["messages"][2]["content"])
            got, ok = args_of(outs[i]["output"])
            valid += ok
            row_ok = True
            for f in FIELDS:
                if f in want and f in got:
                    if same(f, want[f], got[f]):
                        c["correct"] += 1
                    else:
                        c["wrong"] += 1
                        row_ok = False
                elif f in want:
                    c["miss"] += 1
                    row_ok = False
                elif f in got:
                    c["halluc"] += 1
                    row_ok = False
                else:
                    c["empty"] += 1
            exact += row_ok
        gold = c["correct"] + c["wrong"] + c["miss"]
        filled = c["correct"] + c["wrong"] + c["halluc"]
        return (f"rows {len(idx)}  recall {c['correct']}/{gold} ({c['correct'] / max(1, gold):.0%})  "
                f"precision {c['correct']}/{filled} ({c['correct'] / max(1, filled):.0%})  wrong {c['wrong']}  "
                f"halluc {c['halluc']}/{c['halluc'] + c['empty']}  all-fields-right rows {exact}  valid JSON {valid}")

    print("all rows      :", run(range(len(rows))))
    if train:
        print("Jaccard < 0.6 :", run(sorted(far)))


if __name__ == "__main__":
    main()
