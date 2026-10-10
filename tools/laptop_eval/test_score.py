"""Tests for the field scorer. The per-field cases live in a shared fixture that the Kotlin
EvalScorer tests read too, so the two scorers cannot disagree silently."""

import json
from pathlib import Path

import pytest

from score import FIELDS, outcome, score

REPO = Path(__file__).resolve().parents[2]
CASES_PATH = REPO / "core/src/test/resources/fixtures/eval/scorer_cases.jsonl"
CASES = [json.loads(line) for line in CASES_PATH.read_text(encoding="utf-8").splitlines() if line.strip()]


@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
def test_shared_case(case):
    assert outcome(case["field"], case["gold"], case["pred"], case.get("captured_at")) == case["expect"]


DECK_GOLD = {
    "id": "g01",
    "gold": {
        "incident_type": {"status": "REPORTED", "value": "water_leak_flood"},
        "severity": {"status": "MISSING"},
        "location": {"status": "REPORTED", "value": "Conference Room 2"},
        "date_time": {"status": "REPORTED", "date_offset_days": None, "time": "10:00",
                      "part_of_day": None, "approximate": True, "text": "around 10 AM"},
        "persons_involved": {"status": "MISSING"},
        "equipment_asset": {"status": "MISSING"},
        "immediate_actions": {"status": "REPORTED", "value": "isolated the area"},
        "witnesses": {"status": "MISSING"},
    },
    "missing_mandatory": ["severity"],
}

PERFECT_REPORT = {
    "gold_id": "g01",
    "captured_at": "2026-10-11T12:00:00+05:30",
    "report": {"id": "r", "createdAtEpochMs": 0, "fields": {
        "INCIDENT_TYPE": {"text": "water_leak_flood", "status": "REPORTED"},
        "LOCATION": {"text": "Conference Room 2", "status": "REPORTED"},
        "DATE_TIME": {"text": "date missing ≈10:00 (around 10 AM)", "status": "REPORTED"},
        "IMMEDIATE_ACTIONS": {"text": "isolated the area", "status": "REPORTED"},
    }},
    "asked": [["SEVERITY"]],
}

BLANK_REPORT = {"gold_id": "g01", "report": {"id": "r", "createdAtEpochMs": 0, "fields": {}}, "asked": []}


def test_perfect_run_scores_one():
    s = score([DECK_GOLD], [PERFECT_REPORT])
    assert s["overall"]["recall"]["value"] == 1.0
    assert s["overall"]["precision"]["value"] == 1.0
    assert s["overall"]["hallucination_rate"]["value"] == 0.0
    assert s["followup"]["precision"]["value"] == 1.0


def test_blank_run_scores_zero_recall_and_undefined_precision():
    s = score([DECK_GOLD], [BLANK_REPORT])
    assert s["overall"]["recall"] == pytest.approx({"num": 0, "den": 4, "value": 0.0, "ci95": s["overall"]["recall"]["ci95"]})
    assert s["overall"]["precision"]["den"] == 0
    assert s["overall"]["precision"]["value"] is None


def test_missing_prediction_counts_as_failed_item_and_scores_zero():
    s = score([DECK_GOLD], [])
    assert s["n_items"] == 1
    assert s["n_missing_predictions"] == 1
    assert s["overall"]["recall"]["num"] == 0


def test_hallucinated_witness_counts_against_gold_empty_slots():
    pred = json.loads(json.dumps(PERFECT_REPORT))
    pred["report"]["fields"]["WITNESSES"] = {"text": "John", "status": "REPORTED"}
    s = score([DECK_GOLD], [pred])
    # gold-empty slots in the deck example: severity, persons, equipment, witnesses
    assert s["overall"]["hallucination_rate"]["num"] == 1
    assert s["overall"]["hallucination_rate"]["den"] == 4
    assert s["fields"]["witnesses"]["hallucination"] == 1


def test_partial_run_scores_the_right_fraction():
    pred = json.loads(json.dumps(PERFECT_REPORT))
    del pred["report"]["fields"]["LOCATION"]
    s = score([DECK_GOLD], [pred])
    assert (s["overall"]["recall"]["num"], s["overall"]["recall"]["den"]) == (3, 4)
    assert (s["mandatory"]["recall"]["num"], s["mandatory"]["recall"]["den"]) == (2, 3)


def test_followup_asks_for_filled_field_lowers_precision():
    pred = json.loads(json.dumps(PERFECT_REPORT))
    pred["asked"] = [["SEVERITY", "LOCATION"]]
    s = score([DECK_GOLD], [pred])
    assert (s["followup"]["precision"]["num"], s["followup"]["precision"]["den"]) == (1, 2)
    assert (s["followup"]["recall"]["num"], s["followup"]["recall"]["den"]) == (1, 1)


def test_wilson_interval_is_inside_unit_range():
    s = score([DECK_GOLD], [PERFECT_REPORT])
    lo, hi = s["overall"]["recall"]["ci95"]
    assert 0.0 <= lo < 1.0 and hi == pytest.approx(1.0)


def test_every_field_is_reported():
    s = score([DECK_GOLD], [PERFECT_REPORT])
    assert set(s["fields"]) == set(FIELDS)


NONE_GOLD = {"id": "g02", "gold": {"witnesses": {"status": "EXPLICIT_NONE"},
                                    "persons_involved": {"status": "EXPLICIT_NONE"}},
             "missing_mandatory": []}


def test_stated_none_agreement_counts_matching_status_only():
    pred = {"gold_id": "g02", "asked": [], "report": {"id": "r", "createdAtEpochMs": 0, "fields": {
        "WITNESSES": {"text": None, "status": "EXPLICIT_NONE"},
        "PERSONS_INVOLVED": {"text": None, "status": "MISSING"}}}}
    s = score([NONE_GOLD], [pred])
    assert (s["status_agreement"]["EXPLICIT_NONE"]["num"], s["status_agreement"]["EXPLICIT_NONE"]["den"]) == (1, 2)


def test_markdown_table_shows_counts_and_denominators():
    from score import render_markdown
    md = render_markdown(score([DECK_GOLD], [BLANK_REPORT]), title="blank")
    assert "| location |" in md
    assert "0/1" in md          # location recall
    assert "n/a (0/0)" in md    # precision of a blank run
