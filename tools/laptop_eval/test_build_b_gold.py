"""B's synthetic initial reports converted to the gold_v1 shape, so the app's eval runner can read them."""

from build_b_gold import convert


ROW = {"id": "base-x-01-direct", "group_id": "base-x-01", "split": "test", "report_before": {},
       "raw_text": "Water is dripping from the ceiling pipe outside Lab A4. It happened about twenty minutes ago.",
       "target": {"tool": "update_report", "arguments": {
           "incident_type": "water_leak_flood", "severity": "none", "location": "in the corridor outside Lab A4",
           "date_time_text": "about twenty minutes ago", "equipment_asset": "ceiling pipe", "witnesses": ["Sonal"]}},
       "temporal_gold": {"kind": "offset_minutes", "minutes": 20, "approximate": True}}


def test_convert_keeps_b_labels_in_the_gold_shape():
    g = convert(ROW)
    assert g["id"] == "b-base-x-01-direct" and g["split"] == "dev" and g["turns"] == [ROW["raw_text"]]
    assert g["gold"]["equipment_asset"] == {"status": "REPORTED", "value": "ceiling pipe"}
    assert g["gold"]["witnesses"] == {"status": "REPORTED", "value": ["Sonal"]}
    assert g["gold"]["persons_involved"] == {"status": "MISSING"} and g["gold"]["immediate_actions"] == {"status": "MISSING"}
    assert g["gold"]["location"]["value"] == "the corridor outside Lab A4"  # bare leading in/at stripped, as C did
    assert g["gold"]["date_time"]["minutes_ago"] == 20
    assert g["missing_mandatory"] == []


def test_calendar_times_map_and_other_kinds_are_flagged():
    cal = dict(ROW, temporal_gold={"kind": "calendar", "day_offset": -1, "clock": "21:00"})
    dt = convert(cal)["gold"]["date_time"]
    assert dt["date_offset_days"] == -1 and dt["time"] == "21:00"
    other = convert(dict(ROW, temporal_gold={"kind": "previous_weekday", "weekday": "friday"}))
    assert other["date_time_unmapped"] is True
