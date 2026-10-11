"""Schema, convention and double-blind checks for presence_v1.jsonl. Plain unittest (no pytest needed):
    python3 -m unittest docs/experiments/B-20261011-presence-gold/test_presence_gold.py -v
The schema rules are copied from tools/laptop_eval/test_gold.py (E's gold checks) so the file loads in the same scorer."""
import json
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
FILE = Path(__file__).with_name("presence_v1.jsonl")
FIELDS = ["incident_type", "severity", "location", "date_time", "persons_involved", "equipment_asset", "immediate_actions", "witnesses"]
MANDATORY = ["incident_type", "location", "date_time", "severity"]
FILLED = {"REPORTED", "USER_CONFIRMED", "USER_EDITED"}
STATUSES = {"MISSING", "UNKNOWN", "EXPLICIT_NONE", "REPORTED", "SUGGESTED"}
INCIDENT_TYPES = {"water_leak_flood", "fire_smoke", "electrical", "spill_chemical", "injury", "illness", "near_miss",
                  "property_damage", "equipment_failure", "security", "environmental", "other"}
SEVERITIES = {"none", "minor", "moderate", "severe", "critical"}
ITEMS = [json.loads(line) for line in FILE.read_text(encoding="utf-8").splitlines() if line.strip()]


class PresenceGoldTest(unittest.TestCase):
    def test_size_and_unique_ids(self):
        self.assertEqual(16, len(ITEMS))
        self.assertEqual(len(ITEMS), len({i["id"] for i in ITEMS}))

    def test_every_slot_is_well_formed(self):
        for it in ITEMS:
            self.assertEqual("authored_after_failures", it["split"], it["id"])
            self.assertEqual("authored_after_failures", it["source"], it["id"])
            self.assertTrue(it["turns"] and all(t.strip() for t in it["turns"]), it["id"])
            self.assertEqual(set(FIELDS), set(it["gold"]), it["id"])
            for f, slot in it["gold"].items():
                self.assertIn(slot["status"], STATUSES, (it["id"], f))
                if slot["status"] in FILLED:
                    if f == "date_time":
                        self.assertTrue(slot.get("text"), it["id"])
                        self.assertTrue(any(slot.get(k) is not None for k in ("time", "part_of_day", "minutes_ago", "date_offset_days")), it["id"])
                    else:
                        v = slot["value"]
                        if f in ("persons_involved", "witnesses"):
                            self.assertTrue(isinstance(v, list) and v, (it["id"], f))
                        else:
                            self.assertIsInstance(v, str, (it["id"], f))
                if f == "incident_type" and "value" in slot:
                    self.assertLessEqual({slot["value"], *slot.get("alts", [])}, INCIDENT_TYPES, it["id"])
                if f == "severity" and "value" in slot:
                    self.assertIn(slot["value"], SEVERITIES, it["id"])

    def test_missing_mandatory_matches_gold(self):
        for it in ITEMS:
            expected = {f for f in MANDATORY if it["gold"][f]["status"] not in FILLED}
            self.assertEqual(expected, set(it["missing_mandatory"]), it["id"])

    def test_every_filled_value_is_said_in_the_turns(self):
        """A gold value the worker never said would punish a correct, grounded model; alts count."""
        for it in ITEMS:
            said = " ".join(it["turns"]).lower()
            for f in ("location", "equipment_asset", "immediate_actions", "persons_involved", "witnesses"):
                slot = it["gold"][f]
                if slot["status"] != "REPORTED":
                    continue
                values = slot["value"] if isinstance(slot["value"], list) else [slot["value"], *slot.get("alts", [])]
                if isinstance(slot["value"], list):
                    for name in values:
                        self.assertIn(name.lower(), said, (it["id"], f, name))
                else:
                    self.assertTrue(any(v.lower() in said for v in values), (it["id"], f, values))

    def test_presence_convention(self):
        """The labelled intent: presence without sight is a witness; hurt people, roles and absent people are not."""
        by_id = {i["id"]: i for i in ITEMS}
        positives = [i for i in ITEMS if "positive" in i["tags"]]
        controls = [i for i in ITEMS if "control" in i["tags"]]
        self.assertEqual(10, len(positives))
        self.assertEqual(6, len(controls))
        for it in positives:
            self.assertEqual("REPORTED", it["gold"]["witnesses"]["status"], it["id"])
        for it in controls:
            if it["id"] != "p03":  # p03 is the handler judgement call
                self.assertEqual("MISSING", it["gold"]["witnesses"]["status"], it["id"])
        for pid in ("p05", "p06", "p16"):  # hurt people are persons_involved, never witnesses
            self.assertEqual("REPORTED", by_id[pid]["gold"]["persons_involved"]["status"], pid)
        self.assertNotIn("Zoya", by_id["p16"]["gold"].get("witnesses", {}).get("value", []))

    def test_no_blindcheck_words(self):
        patterns = [l.strip() for l in (REPO / "tools/blindcheck.words").read_text(encoding="utf-8").splitlines()
                    if l.strip() and not l.lstrip().startswith("#")]
        text = FILE.read_text(encoding="utf-8") + (FILE.with_name("README.md")).read_text(encoding="utf-8")
        for p in patterns:
            self.assertIsNone(re.search(p, text, re.IGNORECASE), p)


if __name__ == "__main__":
    unittest.main()
