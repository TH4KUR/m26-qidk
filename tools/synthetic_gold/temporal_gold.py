"""Independent, explicitly authored temporal oracle; no app resolver or model calls.

Only phrases in this dataset are supported. Unannotated phrases fail rather than
being guessed. Broad/ambiguous phrases remain broad, even when every form question
has a verbal answer. Capture time is supplied by the evaluator, not by the model.
"""
from __future__ import annotations

import argparse
import copy
import datetime as dt
import json
from pathlib import Path
from zoneinfo import ZoneInfo

ZONE = ZoneInfo("Asia/Kolkata")
SPECS = {}


def annotate(phrases, **spec):
    for phrase in phrases:
        if phrase in SPECS:
            raise ValueError(f"Duplicate temporal annotation: {phrase}")
        SPECS[phrase] = dict(spec)


for phrase, minutes, approximate in [
    ("12 minutes ago", 12, False), ("25 minutes ago", 25, False),
    ("a couple of hours ago", 120, True), ("about an hour ago", 60, True),
    ("about fifteen minutes ago", 15, True), ("about fifty minutes ago", 50, True),
    ("about five minutes ago", 5, True), ("about five minutes back", 5, True),
    ("about half an hour ago", 30, True), ("about ten minutes ago", 10, True),
    ("about twenty minutes ago", 20, True), ("an hour ago", 60, False),
    ("five minutes ago", 5, False), ("half an hour ago", 30, False),
    ("roughly 15 minutes ago", 15, True), ("two hours ago", 120, False),
    ("just now", 0, True), ("right now", 0, False),
]:
    annotate([phrase], kind="offset_minutes", minutes=minutes, approximate=approximate)

for phrase, offset, hour, minute, approximate in [
    ("around 10 AM today", 0, 10, 0, True), ("around 8 AM today", 0, 8, 0, True),
    ("around eleven last night", -1, 23, 0, True),
    ("around half past ten this morning", 0, 10, 30, True),
    ("around noon yesterday", -1, 12, 0, True), ("at 1 PM yesterday", -1, 13, 0, False),
    ("at a quarter to nine this morning", 0, 8, 45, False),
    ("at half past ten yesterday morning", -1, 10, 30, False),
    ("at noon today", 0, 12, 0, False), ("at ten to four yesterday afternoon", -1, 15, 50, False),
    ("at ten yesterday morning", -1, 10, 0, False),
    ("last night at about nine", -1, 21, 0, True), ("today around 7 AM", 0, 7, 0, True),
    ("yesterday around 10 AM", -1, 10, 0, True), ("yesterday at 4 PM", -1, 16, 0, False),
    ("yesterday at 6 PM", -1, 18, 0, False), ("yesterday at 7 PM", -1, 19, 0, False),
]:
    annotate([phrase], kind="calendar", day_offset=offset, clock=f"{hour:02d}:{minute:02d}", approximate=approximate)
annotate(["yesterday at a quarter past eight"], kind="calendar", day_offset=-1,
         approximate=False, ambiguous=True, candidate_times=["08:15", "20:15"],
         reason="Eight has no AM/PM or part-of-day cue; do not choose one.")
annotate(["at 4 PM"], kind="partial_clock", clock="16:00", approximate=False)
annotate(["at 6 PM"], kind="partial_clock", clock="18:00", approximate=False)

for phrase, offset, part in [
    ("yesterday evening", -1, "evening"), ("late last evening", -1, "evening"),
    ("this morning", 0, "morning"), ("this morning before breakfast", 0, "morning"),
    ("after lunch today", 0, "afternoon"), ("the day before yesterday", -2, None),
    ("three days ago", -3, None), ("a week ago", -7, None),
    ("at the start of my shift today", 0, None),
]:
    annotate([phrase], kind="calendar", day_offset=offset, part_of_day=part,
             approximate=phrase in {"three days ago", "a week ago"})
annotate(["last Friday"], kind="previous_weekday", weekday=4, approximate=False)
annotate(["last Thursday afternoon"], kind="previous_weekday", weekday=3,
         part_of_day="afternoon", approximate=False)
annotate(["last week", "last week, sometime after lunch"], kind="week_range", week_offset=-1,
         approximate=True)
annotate(["earlier this week"], kind="week_range", week_offset=0, approximate=True)
annotate(["last weekend"], kind="previous_weekend", approximate=True)
annotate(["on 9 October at about 11 AM"], kind="month_day", month=10, day=9, clock="11:00",
         approximate=True, ambiguous=True, reason="Year was not stated; no year is invented.")
annotate(["on Monday morning"], kind="unresolved", part_of_day="morning", weekday=0,
         ambiguous=True, reason="Which Monday was not stated.")
annotate(["between 2 and 3 PM yesterday"], kind="clock_range", day_offset=-1,
         start_clock="14:00", end_clock="15:00", approximate=True)
annotate(["a few minutes before 9 AM today"], kind="clock_bound", day_offset=0,
         clock="09:00", bound="before", approximate=True)
annotate(["a little after 2 PM yesterday"], kind="clock_bound", day_offset=-1,
         clock="14:00", bound="after", approximate=True)
annotate(["after midnight last night", "just after midnight last night"], kind="unresolved",
         ambiguous=True, approximate=True,
         reason="Night across midnight plus an unspecified offset: retain phrase and clarify date/time.")
for phrase in ["a little while ago", "during the lunch break", "half an hour before lunch",
               "right before the evening shift", "two hours after the morning class"]:
    annotate([phrase], kind="unresolved", approximate=True,
             reason="No numeric offset or reference event clock is supplied.")

# Fully answered variants for the user's all-mandatory-complete training constraint.
# The broader expressions above remain available only for oracle regression tests.
REFINEMENTS = {
    "last Friday": "last Friday at 3 PM",
    "last Thursday afternoon": "last Thursday at 4 PM",
    "last week": "Thursday of last week at 4 PM",
    "last week, sometime after lunch": "Thursday of last week at about 1 PM",
    "last weekend": "last Sunday at 3 PM",
    "earlier this week": "two days ago at 10 AM",
    "on Monday morning": "last Monday at 9 AM",
    "on 9 October at about 11 AM": "on 9 October 2026 at about 11 AM",
    "at 4 PM": "today at 4 PM",
    "at 6 PM": "today at 6 PM",
    "yesterday at a quarter past eight": "yesterday at a quarter past eight in the evening",
    "this morning": "this morning at about 9 AM",
    "this morning before breakfast": "this morning at about 7 AM before breakfast",
    "yesterday evening": "yesterday at about 7 PM",
    "late last evening": "yesterday at about 7:30 PM",
    "after lunch today": "today at about 1:30 PM after lunch",
    "the day before yesterday": "the day before yesterday at 6:30 PM",
    "three days ago": "three days ago at about 3 PM",
    "a week ago": "a week ago at 10 AM",
    "at the start of my shift today": "at 6 AM today, at the start of my shift",
    "between 2 and 3 PM yesterday": "around 2:30 PM yesterday",
    "a few minutes before 9 AM today": "about five minutes before 9 AM today",
    "a little after 2 PM yesterday": "about ten minutes after 2 PM yesterday",
    "a little while ago": "about 25 minutes ago",
    "during the lunch break": "today at about 1 PM, during the lunch break",
    "half an hour before lunch": "half an hour before the 1 PM lunch break today",
    "right before the evening shift": "about ten minutes before the 6 PM shift today",
    "two hours after the morning class": "two hours after the 9 AM class today",
    "after midnight last night": "today at about 12:05 AM",
    "just after midnight last night": "today at about 12:05 AM",
}
for phrase, weekday, clock in [
    ("last Friday at 3 PM", 4, "15:00"), ("last Thursday at 4 PM", 3, "16:00"),
    ("last Sunday at 3 PM", 6, "15:00"), ("last Monday at 9 AM", 0, "09:00"),
]:
    annotate([phrase], kind="previous_weekday", weekday=weekday, clock=clock, approximate=False)
for phrase, clock, approximate in [
    ("Thursday of last week at 4 PM", "16:00", False),
    ("Thursday of last week at about 1 PM", "13:00", True),
]:
    annotate([phrase], kind="weekday_in_week", week_offset=-1, weekday=3, clock=clock, approximate=approximate)
annotate(["on 9 October 2026 at about 11 AM"], kind="absolute", date="2026-10-09", clock="11:00", approximate=True)
for phrase, offset, clock, approximate in [
    ("two days ago at 10 AM", -2, "10:00", False), ("today at 4 PM", 0, "16:00", False),
    ("today at 6 PM", 0, "18:00", False),
    ("yesterday at a quarter past eight in the evening", -1, "20:15", False),
    ("this morning at about 9 AM", 0, "09:00", True),
    ("this morning at about 7 AM before breakfast", 0, "07:00", True),
    ("yesterday at about 7 PM", -1, "19:00", True), ("yesterday at about 7:30 PM", -1, "19:30", True),
    ("today at about 1:30 PM after lunch", 0, "13:30", True),
    ("the day before yesterday at 6:30 PM", -2, "18:30", False),
    ("three days ago at about 3 PM", -3, "15:00", True), ("a week ago at 10 AM", -7, "10:00", False),
    ("at 6 AM today, at the start of my shift", 0, "06:00", False),
    ("around 2:30 PM yesterday", -1, "14:30", True),
    ("today at about 1 PM, during the lunch break", 0, "13:00", True),
    ("today at about 12:05 AM", 0, "00:05", True),
]:
    annotate([phrase], kind="calendar", day_offset=offset, clock=clock, approximate=approximate)
annotate(["about 25 minutes ago"], kind="offset_minutes", minutes=25, approximate=True)
for phrase, offset, anchor, minutes, approximate in [
    ("about five minutes before 9 AM today", 0, "09:00", -5, True),
    ("about ten minutes after 2 PM yesterday", -1, "14:00", 10, True),
    ("half an hour before the 1 PM lunch break today", 0, "13:00", -30, False),
    ("about ten minutes before the 6 PM shift today", 0, "18:00", -10, True),
    ("two hours after the 9 AM class today", 0, "09:00", 120, False),
]:
    annotate([phrase], kind="anchored_clock_offset", day_offset=offset, anchor_clock=anchor,
             offset_minutes=minutes, approximate=approximate)


def time_spec(phrase):
    try:
        return dict(copy.deepcopy(SPECS[phrase]), source_text=phrase)
    except KeyError as error:
        raise ValueError(f"unannotated time phrase: {phrase!r}") from error


def materialize(spec, captured_at):
    if captured_at.tzinfo is None or captured_at.utcoffset() is None:
        raise ValueError("capture time must include a timezone")
    now = captured_at.astimezone(ZONE)
    today = now.date()
    result = {
        "date": None, "time": None, "part_of_day": spec.get("part_of_day"),
        "approximate": spec.get("approximate", False), "ambiguous": spec.get("ambiguous", False),
        "source_text": spec["source_text"], "from": None, "to": None,
    }
    kind = spec["kind"]
    if kind == "offset_minutes":
        value = now - dt.timedelta(minutes=spec["minutes"])
        result.update(date=value.date().isoformat(), time=value.strftime("%H:%M"))
    elif kind == "calendar":
        result.update(date=(today + dt.timedelta(days=spec["day_offset"])).isoformat(), time=spec.get("clock"))
    elif kind == "partial_clock":
        result["time"] = spec["clock"]
    elif kind == "previous_weekday":
        days_back = (today.weekday() - spec["weekday"]) % 7 or 7
        result.update(date=(today - dt.timedelta(days=days_back)).isoformat(), time=spec.get("clock"))
    elif kind == "weekday_in_week":
        monday = today - dt.timedelta(days=today.weekday())
        date = monday + dt.timedelta(weeks=spec["week_offset"], days=spec["weekday"])
        result.update(date=date.isoformat(), time=spec["clock"])
    elif kind == "absolute":
        result.update(date=spec["date"], time=spec["clock"])
    elif kind == "anchored_clock_offset":
        date = today + dt.timedelta(days=spec["day_offset"])
        anchor = dt.datetime.combine(date, dt.time.fromisoformat(spec["anchor_clock"]), ZONE)
        value = anchor + dt.timedelta(minutes=spec["offset_minutes"])
        result.update(date=value.date().isoformat(), time=value.strftime("%H:%M"))
    elif kind == "week_range":
        monday = today - dt.timedelta(days=today.weekday())
        start = monday + dt.timedelta(weeks=spec["week_offset"])
        end = today if spec["week_offset"] == 0 else start + dt.timedelta(days=6)
        result.update({"from": start.isoformat(), "to": end.isoformat()})
    elif kind == "previous_weekend":
        monday = today - dt.timedelta(days=today.weekday())
        result.update({"from": (monday - dt.timedelta(days=2)).isoformat(), "to": (monday - dt.timedelta(days=1)).isoformat()})
    elif kind == "month_day":
        result.update(time=spec.get("clock"), month=spec["month"], day_of_month=spec["day"])
    elif kind in {"clock_range", "clock_bound"}:
        date = today + dt.timedelta(days=spec["day_offset"])
        result["date"] = date.isoformat()
        if kind == "clock_range":
            result.update(start_time=spec["start_clock"], end_time=spec["end_clock"])
        else:
            result.update(bound_time=spec["clock"], bound=spec["bound"])
    elif kind != "unresolved":
        raise ValueError(f"unknown temporal gold kind: {kind}")
    for key in ("reason", "candidate_times", "weekday"):
        if key in spec:
            result[key] = spec[key]
    result["needs_clarification"] = result["date"] is None or result["time"] is None or result["ambiguous"]
    return result


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold", type=Path, default=root / "data/synthetic_gold/v1/gold.jsonl")
    clock = parser.add_mutually_exclusive_group()
    clock.add_argument("--captured-at", help="Timezone-aware ISO time, or now (default); one shared anchor for this materialization")
    clock.add_argument("--captures", type=Path, help="JSONL of {id,captured_at} from each actual app/evaluator capture")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve() == args.gold.resolve():
        parser.error("output must not overwrite the source gold")
    rows = [json.loads(line) for line in args.gold.read_text(encoding="utf-8").splitlines()]
    if args.captures:
        captures = {}
        for line in args.captures.read_text(encoding="utf-8").splitlines():
            row = json.loads(line)
            if row["id"] in captures:
                raise ValueError(f"duplicate capture id: {row['id']}")
            captures[row["id"]] = dt.datetime.fromisoformat(row["captured_at"].replace("Z", "+00:00"))
        if set(captures) != {r["id"] for r in rows}:
            raise ValueError("capture ids must exactly match supplied gold ids; pass the evaluated subset with --gold")
    else:
        clock_text = args.captured_at or "now"
        anchor = dt.datetime.now(ZONE) if clock_text == "now" else dt.datetime.fromisoformat(clock_text.replace("Z", "+00:00"))
        captures = {r["id"]: anchor for r in rows}
    outputs = [{"id": row["id"], "captured_at": captures[row["id"]].isoformat(),
                "expected_extraction": row["target"],
                "expected_date_time": materialize(row["temporal_gold"], captures[row["id"]])} for row in rows]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in outputs), encoding="utf-8")
    print(json.dumps({"rows": len(outputs), "output": str(args.output), "clock_source": "recorded per-row captures" if args.captures else "shared evaluator anchor"}))


if __name__ == "__main__":
    main()
