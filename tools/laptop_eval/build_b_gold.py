"""B's synthetic initial reports (validation + test, 134 rows) in the gold_v1 shape, for a larger equipment check.

Labels are B's (`b/speech-engines` 51c1124, `data/synthetic_gold/v1/gold.jsonl`), machine-checked by C (C/010),
not human-adjudicated. Every row is marked split "dev" so the app's eval runner reads it; it is NOT our dev set.
Only the four optional fields are meant to be compared on this file: date_time maps for "N minutes ago" and
calendar day+clock labels; other time kinds keep only the spoken text and carry date_time_unmapped=true.

    git show origin/b/speech-engines:data/synthetic_gold/v1/gold.jsonl > /tmp/b_gold.jsonl
    python3 build_b_gold.py /tmp/b_gold.jsonl   # writes for_a/b_synthetic134_gold.jsonl
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "for_a" / "b_synthetic134_gold.jsonl"
VERBATIM = ["severity", "equipment_asset", "immediate_actions"]


def _time(row: dict) -> tuple[dict, bool]:
    a = row["target"]["arguments"]
    dt = {"status": "REPORTED", "text": a.get("date_time_text", ""), "date_offset_days": None, "time": None,
          "part_of_day": None, "approximate": bool((row.get("temporal_gold") or {}).get("approximate"))}
    t = row.get("temporal_gold") or {}
    if t.get("kind") == "offset_minutes":
        dt["minutes_ago"] = t["minutes"]
        return dt, False
    if t.get("kind") == "calendar":
        dt["date_offset_days"] = t.get("day_offset")
        dt["time"] = t.get("clock")
        dt["part_of_day"] = t.get("part_of_day")
        return dt, False
    return dt, True


def convert(row: dict) -> dict:
    a = row["target"]["arguments"]
    gold = {}
    gold["incident_type"] = {"status": "REPORTED", "value": a["incident_type"]} if a.get("incident_type") else {"status": "MISSING"}
    loc = re.sub(r"^(in|at)\s+", "", a.get("location", ""), flags=re.I)
    gold["location"] = {"status": "REPORTED", "value": loc} if loc else {"status": "MISSING"}
    dt, unmapped = _time(row) if a.get("date_time_text") else ({"status": "MISSING"}, False)
    gold["date_time"] = dt
    for f in VERBATIM:
        gold[f] = {"status": "REPORTED", "value": a[f]} if a.get(f) else {"status": "MISSING"}
    for f in ("persons_involved", "witnesses"):
        gold[f] = {"status": "REPORTED", "value": list(a[f])} if a.get(f) else {"status": "MISSING"}
    out = {"id": f"b-{row['id']}", "split": "dev", "source": "b_synthetic_51c1124", "family": row["group_id"],
           "b_split": row["split"], "language": "en", "turns": [row["raw_text"]], "gold": gold,
           "missing_mandatory": [f for f in ("incident_type", "location", "date_time", "severity") if gold[f]["status"] == "MISSING"]}
    if unmapped:
        out["date_time_unmapped"] = True
    return out


def main() -> None:
    rows = [json.loads(l) for l in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines() if l.strip()]
    keep = [convert(r) for r in rows if not r["report_before"] and r["split"] in ("validation", "test")]
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text("".join(json.dumps(g, ensure_ascii=False) + "\n" for g in keep), encoding="utf-8")
    print(f"{OUT.name}: {len(keep)} rows, {sum(g.get('date_time_unmapped', False) for g in keep)} with an unmapped time kind")


if __name__ == "__main__":
    main()
