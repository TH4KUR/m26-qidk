"""Word and character error rates for recorded clips.

normalize() follows B's tools/accent_contest/contest.py (NFKC, casefold, punctuation to spaces), so English WER
agrees with B's accent table. Punctuation is removed by Unicode category (letters, combining marks and digits are
kept): Python's regex word class drops Indic vowel signs and would split Hindi/Telugu words into consonants (A/033). Hindi and
Telugu also get a character error rate. Aggregates are micro-averaged: total errors / total reference units.

    python3 wer.py --manifest app/src/main/assets/eval/manifest.jsonl --hyp transcripts.jsonl
    (transcripts.jsonl: one {"clip_id": ..., "text": ...} per line)
"""

from __future__ import annotations

import argparse
import json
import unicodedata
from pathlib import Path


INDIC = {"hi", "te"}


def normalize(text: str) -> list[str]:
    text = unicodedata.normalize("NFKC", text).casefold()
    kept = (ch if ch.isspace() or unicodedata.category(ch)[0] in "LMN" else " " for ch in text)
    return "".join(kept).split()


def edit_distance(reference: list, hypothesis: list) -> int:
    previous = list(range(len(hypothesis) + 1))
    for index, expected in enumerate(reference, start=1):
        current = [index]
        for column, actual in enumerate(hypothesis, start=1):
            current.append(min(previous[column] + 1, current[column - 1] + 1, previous[column - 1] + (expected != actual)))
        previous = current
    return previous[-1]


def wer_counts(reference: str, hypothesis: str) -> tuple[int, int]:
    ref = normalize(reference)
    return edit_distance(ref, normalize(hypothesis)), len(ref)


def cer_counts(reference: str, hypothesis: str) -> tuple[int, int]:
    """Character edits and reference characters over NFC code points, whitespace removed (Hindi/Telugu)."""
    ref = list(unicodedata.normalize("NFC", "".join(reference.split())))
    hyp = list(unicodedata.normalize("NFC", "".join(hypothesis.split())))
    return edit_distance(ref, hyp), len(ref)


def cer(reference: str, hypothesis: str) -> float:
    errors, chars = cer_counts(reference, hypothesis)
    return errors / chars if chars else 0.0


def aggregate(pairs) -> dict:
    errors = words = 0
    for ref, hyp in pairs:
        e, n = wer_counts(ref, hyp)
        errors += e
        words += n
    return {"clips": len(pairs), "errors": errors, "reference_words": words, "wer": errors / words if words else None}


def score_transcripts(manifest_path, hypotheses: dict) -> dict:
    rows = [json.loads(l) for l in Path(manifest_path).read_text(encoding="utf-8").splitlines() if l.strip()]
    usable = [r for r in rows if r.get("reference") and r["clip_id"] in hypotheses]
    skipped = [r["clip_id"] for r in rows if not r.get("reference")]

    def group(key):
        out = {}
        for value in sorted({r.get(key, "") for r in usable}):
            pairs = [(r["reference"], hypotheses[r["clip_id"]]) for r in usable if r.get(key, "") == value]
            out[value] = aggregate(pairs)
            if key == "language" and value in INDIC:
                errors = chars = 0
                for ref, hyp in pairs:
                    e, n = cer_counts(ref, hyp)
                    errors, chars = errors + e, chars + n
                out[value]["cer"] = errors / chars if chars else None
        return out

    return {"all": aggregate([(r["reference"], hypotheses[r["clip_id"]]) for r in usable]),
            "by_device": group("device"), "by_language": group("language"), "by_condition": group("condition"),
            "skipped_no_reference": skipped,
            "missing_hypothesis": [r["clip_id"] for r in rows if r.get("reference") and r["clip_id"] not in hypotheses]}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--hyp", required=True, help="jsonl of {clip_id, text}")
    args = ap.parse_args()
    hyp = {r["clip_id"]: r["text"] for r in (json.loads(l) for l in Path(args.hyp).read_text(encoding="utf-8").splitlines() if l.strip())}
    print(json.dumps(score_transcripts(args.manifest, hyp), indent=1))


if __name__ == "__main__":
    main()
