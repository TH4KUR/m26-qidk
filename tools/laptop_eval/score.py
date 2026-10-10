"""Field-level scorer for incident reports (rules: docs/eval-guide.md, section "Scoring").

Gold items come from gold_v1.jsonl; predictions are C's serialized IncidentReport wrapped as
{"gold_id", "captured_at", "report", "asked"}. Every ratio is returned with its numerator,
denominator and a Wilson 95% interval; a ratio with no denominator has value None, never 1.0.
"""

from __future__ import annotations

import math
import re
import unicodedata
from datetime import date, datetime, time, timedelta

FIELDS = [
    "incident_type", "severity", "location", "date_time",
    "persons_involved", "equipment_asset", "immediate_actions", "witnesses",
]
MANDATORY = ["incident_type", "location", "date_time", "severity"]  # spec D6
REPORT_KEY = {f: f.upper() for f in FIELDS}  # Field enum names; date_time_text <-> DATE_TIME

ENUM_FIELDS = {"incident_type", "severity"}
LIST_FIELDS = {"persons_involved", "witnesses"}
FILLED = {"REPORTED", "USER_CONFIRMED", "USER_EDITED"}
ANSWERED = FILLED | {"EXPLICIT_NONE", "UNKNOWN"}
SPECIAL_STATUSES = ("EXPLICIT_NONE", "UNKNOWN", "SUGGESTED")  # gold says "answered but not a value"

# Same stop-words as C's GroundingValidator, so "near Conference Room 2" matches "Conference Room 2".
STOP_WORDS = {"the", "a", "an", "of", "in", "at", "near", "around", "to", "on", "is", "was", "and"}
NUMBER_WORDS = {w: str(i) for i, w in enumerate(
    "zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
    "fifteen sixteen seventeen eighteen nineteen twenty".split())}
TEXT_MATCH = 0.8

PARTS_OF_DAY = ("morning", "afternoon", "evening", "night")
DISPLAY = re.compile(
    r"^\s*(?P<date>\d{4}-\d{2}-\d{2}|date missing)\s+≈?\s*"
    r"(?P<tod>\d{1,2}:\d{2}|morning|afternoon|evening|night|time missing)",
    re.IGNORECASE,
)


# ---- normalisation -------------------------------------------------------------------------

def tokens(text: str) -> list[str]:
    """NFC, lower-case, split on anything that is not a letter, mark or digit (as C does)."""
    text = unicodedata.normalize("NFC", text).lower()
    out, cur = [], []
    for ch in text:
        if unicodedata.category(ch)[0] in "LMN":
            cur.append(ch)
        elif cur:
            out.append("".join(cur))
            cur = []
    if cur:
        out.append("".join(cur))
    return [NUMBER_WORDS.get(t, t) for t in out]


def content(text: str) -> set[str]:
    toks = tokens(text)
    kept = {t for t in toks if t not in STOP_WORDS}
    return kept or set(toks)


def dice(a: set[str], b: set[str]) -> float:
    if not a or not b:
        return 0.0
    return 2 * len(a & b) / (len(a) + len(b))


def names(text_or_list) -> set[str]:
    parts = text_or_list if isinstance(text_or_list, list) else re.split(r",|&|\band\b", text_or_list)
    return {" ".join(sorted(content(p))) for p in parts if tokens(p)}


# ---- date and time -------------------------------------------------------------------------

def _pred_time_parts(pred: dict):
    """Returns (date|None, "HH:MM"|None, part_of_day|None), or None when unparseable."""
    parts = pred.get("parts")
    if parts:
        d = date.fromisoformat(parts["date"]) if parts.get("date") else None
        pod = (parts.get("partOfDay") or parts.get("part_of_day") or None)
        return d, parts.get("time"), pod.lower() if pod else None
    m = DISPLAY.match(pred.get("text") or "")
    if not m:
        return None
    d = None if m["date"].lower() == "date missing" else date.fromisoformat(m["date"])
    tod = m["tod"].lower()
    if tod in PARTS_OF_DAY:
        return d, None, tod
    if tod == "time missing":
        return d, None, None
    h, mi = tod.split(":")
    return d, f"{int(h):02d}:{mi}", None


def _date_time_matches(gold: dict, pred: dict, captured_at: str | None) -> bool:
    parsed = _pred_time_parts(pred)
    if parsed is None:
        return False
    d, t, pod = parsed
    if gold.get("minutes_ago") is not None:
        # "N minutes ago" resolves against the capture instant: compare date and clock time (±1 min).
        if not captured_at or d is None or t is None:
            return False
        expected = datetime.fromisoformat(captured_at) - timedelta(minutes=gold["minutes_ago"])
        got = datetime.combine(d, time.fromisoformat(t), expected.tzinfo)
        return abs((got - expected).total_seconds()) <= 60
    offset = gold.get("date_offset_days")
    if offset is None:
        date_ok = d is None
    else:
        ref = datetime.fromisoformat(captured_at).date() if captured_at else None
        date_ok = d is not None and ref is not None and (d - ref).days == offset
    if gold.get("time"):
        time_ok = t == gold["time"]
    elif gold.get("part_of_day"):
        time_ok = t is None and pod == gold["part_of_day"].lower()
    else:
        time_ok = t is None
    return date_ok and time_ok


# ---- per-slot outcome ----------------------------------------------------------------------

def _is_filled(pred: dict | None) -> bool:
    return bool(pred) and pred.get("status") in FILLED and bool((pred.get("text") or "").strip() or pred.get("parts"))


def _matches(field: str, gold: dict, pred: dict, captured_at: str | None) -> bool:
    text = pred.get("text") or ""
    if field == "date_time":
        if pred.get("raw"):  # raw LLM config: no resolver ran, so score the time phrase itself
            return dice(content(text), content(gold.get("text") or "")) >= TEXT_MATCH
        return _date_time_matches(gold, pred, captured_at)
    if field in ENUM_FIELDS:
        return text.strip().lower() in {str(v).lower() for v in [gold["value"], *gold.get("alts", [])]}
    if field in LIST_FIELDS:
        return names(text) == names(gold["value"])
    candidates = [gold["value"], *gold.get("alts", [])]
    return any(dice(content(text), content(c)) >= TEXT_MATCH for c in candidates)


def outcome(field: str, gold: dict, pred: dict | None, captured_at: str | None = None) -> str:
    """One of: correct, wrong, miss, hallucination, correct_empty."""
    gold_has_value = gold.get("status") in FILLED
    filled = _is_filled(pred)
    if gold_has_value:
        if not filled:
            return "miss"
        return "correct" if _matches(field, gold, pred, captured_at) else "wrong"
    return "hallucination" if filled else "correct_empty"


# ---- aggregation ---------------------------------------------------------------------------

def wilson(num: int, den: int, z: float = 1.96) -> list[float] | None:
    if den == 0:
        return None
    p = num / den
    centre = (p + z * z / (2 * den)) / (1 + z * z / den)
    half = z * math.sqrt(p * (1 - p) / den + z * z / (4 * den * den)) / (1 + z * z / den)
    return [max(0.0, centre - half), min(1.0, centre + half)]


def ratio(num: int, den: int) -> dict:
    return {"num": num, "den": den, "value": (num / den) if den else None, "ci95": wilson(num, den)}


OUTCOMES = ("correct", "wrong", "miss", "hallucination", "correct_empty")


def _summarise(counts: dict) -> dict:
    c = dict(counts)
    c["gold_values"] = c["correct"] + c["wrong"] + c["miss"]
    c["filled"] = c["correct"] + c["wrong"] + c["hallucination"]
    c["precision"] = ratio(c["correct"], c["filled"])
    c["recall"] = ratio(c["correct"], c["gold_values"])
    c["hallucination_rate"] = ratio(c["hallucination"], c["hallucination"] + c["correct_empty"])
    return c


def _asked_fields(pred: dict | None) -> set[str]:
    if pred is None:
        return set()
    if "asked" in pred:
        return {f.lower() for turn in pred["asked"] for f in turn}
    # Text-only runs have no follow-up planner: use the mandatory fields left unanswered.
    fields = pred["report"].get("fields", {})
    return {f for f in MANDATORY if (fields.get(REPORT_KEY[f]) or {}).get("status") not in ANSWERED}


def score(golds: list[dict], preds: list[dict]) -> dict:
    by_id = {p["gold_id"]: p for p in preds}
    per_field = {f: dict.fromkeys(OUTCOMES, 0) for f in FIELDS}
    missing_preds = 0
    fu_hit = fu_asked = fu_missing = 0
    agree = {st: [0, 0] for st in SPECIAL_STATUSES}

    for item in golds:
        pred = by_id.get(item["id"])
        if pred is None:
            missing_preds += 1
        fields = (pred or {}).get("report", {}).get("fields", {})
        captured_at = (pred or {}).get("captured_at")
        for f in FIELDS:
            gold = item["gold"].get(f, {"status": "MISSING"})
            pv = fields.get(REPORT_KEY[f])
            per_field[f][outcome(f, gold, pv, captured_at)] += 1
            if gold["status"] in agree:
                agree[gold["status"]][1] += 1
                agree[gold["status"]][0] += (pv or {}).get("status", "MISSING") == gold["status"]

        asked = _asked_fields(pred)
        missing = set(item.get("missing_mandatory", []))
        fu_hit += len(asked & missing)
        fu_asked += len(asked)
        fu_missing += len(missing)

    def total(subset):
        return _summarise({o: sum(per_field[f][o] for f in subset) for o in OUTCOMES})

    return {
        "n_items": len(golds),
        "n_missing_predictions": missing_preds,
        "fields": {f: _summarise(per_field[f]) for f in FIELDS},
        "overall": total(FIELDS),
        "mandatory": total(MANDATORY),
        "followup": {"precision": ratio(fu_hit, fu_asked), "recall": ratio(fu_hit, fu_missing)},
        "status_agreement": {st: ratio(*agree[st]) for st in SPECIAL_STATUSES},
    }


def _fmt(r: dict) -> str:
    if r["value"] is None:
        return "n/a (0/0)"
    return f"{r['num']}/{r['den']} ({100 * r['value']:.0f}%)"


def render_markdown(s: dict, title: str = "") -> str:
    lines = [f"### {title}" if title else "", f"Items: {s['n_items']} (missing predictions: {s['n_missing_predictions']})", "",
             "| field | precision | recall | hallucination | correct / wrong / miss |", "|---|---|---|---|---|"]
    for name, row in [*s["fields"].items(), ("**mandatory**", s["mandatory"]), ("**overall**", s["overall"])]:
        lines.append(f"| {name} | {_fmt(row['precision'])} | {_fmt(row['recall'])} | "
                     f"{_fmt(row['hallucination_rate'])} | {row['correct']} / {row['wrong']} / {row['miss']} |")
    fu = s["followup"]
    lines += ["", f"Follow-up precision {_fmt(fu['precision'])}, recall {_fmt(fu['recall'])}"]
    for st, r in s["status_agreement"].items():
        lines.append(f"{st} agreement {_fmt(r)}")
    return "\n".join(lines).lstrip() + "\n"
