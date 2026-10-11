import datetime as dt
import unittest

import temporal_gold as gold


class TemporalGoldTest(unittest.TestCase):
    def test_offset_crosses_midnight_using_recorded_capture(self):
        capture = dt.datetime.fromisoformat("2026-10-11T00:10:00+05:30")
        result = gold.materialize(gold.time_spec("about twenty minutes ago"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-10-10", "23:50"))
        self.assertTrue(result["approximate"])

    def test_time_zone_is_independent_of_machine_timezone(self):
        capture = dt.datetime.fromisoformat("2026-10-10T18:40:00+00:00")
        result = gold.materialize(gold.time_spec("about twenty minutes ago"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-10-10", "23:50"))

    def test_dynamic_yesterday_changes_with_capture_date(self):
        capture = dt.datetime.fromisoformat("2027-01-01T08:00:00+05:30")
        result = gold.materialize(gold.time_spec("yesterday at 6 PM"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-12-31", "18:00"))

    def test_day_only_does_not_invent_time(self):
        capture = dt.datetime.fromisoformat("2026-10-11T11:00:00+05:30")
        result = gold.materialize(gold.time_spec("last Friday"), capture)
        self.assertEqual(result["date"], "2026-10-09")
        self.assertIsNone(result["time"])
        self.assertTrue(result["needs_clarification"])

    def test_week_is_a_range_and_undated_clock_stays_undated(self):
        capture = dt.datetime.fromisoformat("2026-10-11T11:00:00+05:30")
        result = gold.materialize(gold.time_spec("last week"), capture)
        self.assertEqual((result["from"], result["to"]), ("2026-09-28", "2026-10-04"))
        self.assertIsNone(result["date"])
        result = gold.materialize(gold.time_spec("at 6 PM"), capture)
        self.assertEqual(result["time"], "18:00")
        self.assertIsNone(result["date"])

    def test_vague_offset_is_not_assigned_arbitrary_minutes(self):
        capture = dt.datetime.fromisoformat("2026-10-11T11:00:00+05:30")
        result = gold.materialize(gold.time_spec("a little while ago"), capture)
        self.assertIsNone(result["date"])
        self.assertIsNone(result["time"])
        self.assertTrue(result["needs_clarification"])

    def test_rejects_naive_capture_or_unannotated_phrase(self):
        with self.assertRaisesRegex(ValueError, "timezone"):
            gold.materialize(gold.time_spec("just now"), dt.datetime(2026, 10, 11))
        with self.assertRaisesRegex(ValueError, "unannotated"):
            gold.time_spec("a trillion years ago")

    def test_resolves_offset_from_explicit_clock_reference(self):
        capture = dt.datetime.fromisoformat("2026-10-11T11:00:00+05:30")
        result = gold.materialize(gold.time_spec("about five minutes before 9 AM today"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-10-11", "08:55"))
        result = gold.materialize(gold.time_spec("two hours after the 9 AM class today"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-10-11", "11:00"))

    def test_weekday_of_last_week_crosses_year_boundary(self):
        capture = dt.datetime.fromisoformat("2027-01-01T11:00:00+05:30")
        result = gold.materialize(gold.time_spec("Thursday of last week at 4 PM"), capture)
        self.assertEqual((result["date"], result["time"]), ("2026-12-24", "16:00"))
        self.assertFalse(result["needs_clarification"])


if __name__ == "__main__":
    unittest.main()
