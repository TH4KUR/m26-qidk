"""Data-quality regressions: fail closed before bad labels reach SFT."""
import copy
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


if __name__ == "__main__":
    unittest.main()
