"""Data-quality regressions: fail closed before bad labels reach SFT."""
import copy
import json
import unittest

import build_dataset as dataset


class DatasetValidationTest(unittest.TestCase):
    def example(self):
        return {
            "id": "test-1", "group_id": "test", "split": "train",
            "raw_text": "Smoke from the printer near Lab C4.", "report_before": {},
            "target": {"tool": "update_report", "arguments": {
                "incident_type": "fire_smoke", "location": "near Lab C4",
                "equipment_asset": "printer", "evidence": {
                    "incident_type": "Smoke", "location": "near Lab C4",
                    "equipment_asset": "printer"}}},
        }

    def test_accepts_sparse_grounded_target_and_noop(self):
        dataset.validate_rows([self.example()])
        row = self.example()
        row["target"]["arguments"] = {}
        dataset.validate_rows([row])

    def test_rejects_fabricated_evidence(self):
        row = self.example()
        row["target"]["arguments"]["evidence"]["location"] = "Room 20"
        with self.assertRaisesRegex(ValueError, "evidence"):
            dataset.validate_rows([row])

    def test_rejects_evidence_without_corresponding_field(self):
        row = self.example()
        del row["target"]["arguments"]["location"]
        with self.assertRaisesRegex(ValueError, "evidence"):
            dataset.validate_rows([row])

    def test_rejects_unsupported_or_empty_values(self):
        for value in (None, "", [], "Room 20"):
            with self.subTest(value=value):
                row = self.example()
                row["target"]["arguments"]["location"] = value
                with self.assertRaises(ValueError):
                    dataset.validate_rows([row])

    def test_rejects_unknown_field_or_enum(self):
        row = self.example()
        row["target"]["arguments"]["description"] = "A fire."
        with self.assertRaises(ValueError):
            dataset.validate_rows([row])
        row = self.example()
        row["target"]["arguments"]["incident_type"] = "catastrophe"
        with self.assertRaises(ValueError):
            dataset.validate_rows([row])

    def test_rejects_group_and_exact_input_leakage(self):
        for same_group in (True, False):
            row = self.example()
            other = copy.deepcopy(row)
            other.update(id="test-2", split="test")
            if same_group:
                other["raw_text"] += " I am here."
            else:
                other["group_id"] = "different"
            with self.assertRaisesRegex(ValueError, "leakage"):
                dataset.validate_rows([row, other])

    def test_rejects_repeated_saved_scalar(self):
        row = self.example()
        row["report_before"]["location"] = "near Lab C4"
        with self.assertRaisesRegex(ValueError, "unchanged"):
            dataset.validate_rows([row])

    def test_keeps_time_phrase_and_proximity_literal(self):
        row = self.example()
        row["raw_text"] += " around 10 AM"
        args = row["target"]["arguments"]
        args["date_time_text"] = "2026-10-11T10:00:00"
        args["evidence"]["date_time_text"] = "around 10 AM"
        with self.assertRaises(ValueError):
            dataset.validate_rows([row])

    def test_rejects_incomplete_mandatory_answers_annotation(self):
        row = self.example()
        row["mandatory_answers"] = {"incident_type": {"value": "fire_smoke", "evidence": "Smoke"}}
        with self.assertRaisesRegex(ValueError, "four mandatory"):
            dataset.validate_rows([row])

    def transition_pair(self):
        first = self.example()
        after = {"incident_type": "fire_smoke", "location": "near Lab C4", "equipment_asset": "printer"}
        first["transition"] = {
            "turn_index": 0, "previous_id": None, "operation": "initial",
            "changed_fields": ["incident_type", "location", "equipment_asset"],
            "captured_at": "2026-10-11T11:00:00+05:30", "expected_report_after": after,
        }
        second = copy.deepcopy(first)
        second.update(id="test-2", raw_text="Correction: the place was inside Lab C4.", report_before=after)
        second["target"]["arguments"] = {
            "location": "inside Lab C4", "evidence": {"location": "inside Lab C4"}}
        second["transition"] = {
            "turn_index": 1, "previous_id": first["id"], "operation": "replace",
            "changed_fields": ["location"], "captured_at": "2026-10-11T11:00:00+05:30",
            "expected_report_after": dict(after, location="inside Lab C4"),
        }
        return [first, second]

    def test_rejects_stale_saved_state_after_prior_update(self):
        rows = self.transition_pair()
        rows[1]["report_before"] = dict(rows[1]["report_before"], equipment_asset="fan")
        with self.assertRaisesRegex(ValueError, "previous.*state|stale"):
            dataset.validate_rows(rows)

    def test_rejects_patch_that_cannot_produce_authored_final_state(self):
        rows = self.transition_pair()
        rows[1]["transition"]["expected_report_after"]["location"] = "near Lab C4"
        with self.assertRaisesRegex(ValueError, "expected.*state|patch"):
            dataset.validate_rows(rows)

    def test_rejects_missing_update_in_declared_changed_fields(self):
        rows = self.transition_pair()
        rows[1]["transition"]["changed_fields"] = []
        with self.assertRaisesRegex(ValueError, "changed.fields"):
            dataset.validate_rows(rows)

    def test_committed_corpus_exercises_repeated_updates_to_every_field(self):
        rows = [json.loads(line) for line in (dataset.DEST / "gold.jsonl").read_text().splitlines()]
        sequences = {}
        for row in rows:
            if "transition" in row:
                sequences.setdefault(row["group_id"], []).append(row)
        self.assertEqual(80, len(sequences), "Missing the update-heavy sequence corpus")
        for sequence in sequences.values():
            self.assertEqual([0, 1, 2, 3], [r["transition"]["turn_index"] for r in sequence])
            self.assertTrue(sequence[1]["report_before"])
            self.assertTrue(sequence[2]["report_before"])
            self.assertEqual({}, sequence[-1]["target"]["arguments"])
            self.assertEqual(1, len({r["split"] for r in sequence}))
        for field in dataset.FIELDS:
            changed = [r for seq in sequences.values() for r in seq[1:]
                       if field in r["transition"]["changed_fields"]]
            self.assertGreaterEqual(len(changed), 16, field)


if __name__ == "__main__":
    unittest.main()
