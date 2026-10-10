"""Schema, coverage and double-blind checks for the gold set (docs/eval-guide.md)."""

import json
import re
from collections import Counter
from pathlib import Path

import pytest

from score import FIELDS, FILLED, MANDATORY

REPO = Path(__file__).resolve().parents[2]
GOLD = REPO / "app/src/main/assets/eval/gold_v1.jsonl"
HOMOPHONES = REPO / "app/src/main/assets/eval/gold_homophones.jsonl"
INCIDENT_TYPES = {"water_leak_flood", "fire_smoke", "electrical", "spill_chemical", "injury", "illness", "near_miss",
                  "property_damage", "equipment_failure", "security", "environmental", "other"}
SEVERITIES = {"none", "minor", "moderate", "severe", "critical"}
STATUSES = {"MISSING", "UNKNOWN", "EXPLICIT_NONE", "REPORTED", "SUGGESTED"}
SOURCES = {"handwritten", "broad", "osha", "multiturn", "homophone"}


@pytest.fixture(scope="module")
def items():
    lines = [l for l in GOLD.read_text(encoding="utf-8").splitlines() if l.strip()]
    return [json.loads(l) for l in lines]


def test_size_and_unique_ids(items):
    assert len(items) == 40
    assert len({i["id"] for i in items}) == 40


def test_deck_example_is_g01_verbatim(items):
    g01 = items[0]
    assert g01["id"] == "g01" and g01["split"] == "dev"
    assert g01["turns"] == ["There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."]


def test_every_slot_is_well_formed(items):
    for it in items:
        assert it["source"] in SOURCES, it["id"]
        assert it["split"] in {"dev", "holdout"}, it["id"]
        assert it["turns"] and all(t.strip() for t in it["turns"]), it["id"]
        assert set(it["gold"]) == set(FIELDS), it["id"]
        for f, slot in it["gold"].items():
            st = slot["status"]
            assert st in STATUSES, (it["id"], f)
            if st in FILLED | {"SUGGESTED"}:
                if f == "date_time":
                    assert slot.get("vague") or any(slot.get(k) is not None for k in ("time", "part_of_day", "minutes_ago", "date_offset_days")), it["id"]
                    assert slot.get("text"), it["id"]
                else:
                    v = slot["value"]
                    assert (isinstance(v, list) and v) if f in ("persons_involved", "witnesses") else isinstance(v, str), (it["id"], f)
            if st == "EXPLICIT_NONE":
                assert f in ("persons_involved", "witnesses"), (it["id"], f)  # C's validator emits it only there
            if f == "incident_type" and "value" in slot:
                assert {slot["value"], *slot.get("alts", [])} <= INCIDENT_TYPES, it["id"]
            if f == "severity" and "value" in slot:
                assert slot["value"] in SEVERITIES, it["id"]


def test_missing_mandatory_matches_gold_for_single_turn_items(items):
    for it in items:
        assert set(it["missing_mandatory"]) <= set(MANDATORY), it["id"]
        if len(it["turns"]) == 1:
            expected = {f for f in MANDATORY if it["gold"][f]["status"] not in FILLED}
            assert set(it["missing_mandatory"]) == expected, it["id"]
        else:
            assert it["missing_mandatory"], it["id"]  # a multi-turn item exists to exercise a follow-up


def test_coverage(items):
    sources = Counter(i["source"] for i in items)
    assert sources == {"handwritten": 10, "broad": 10, "osha": 12, "multiturn": 8}
    statuses = Counter(s["status"] for i in items for s in i["gold"].values())
    assert statuses["EXPLICIT_NONE"] >= 4 and statuses["UNKNOWN"] >= 1 and statuses["SUGGESTED"] >= 2
    types = Counter(i["gold"]["incident_type"]["value"] for i in items)
    assert sum(1 for t in INCIDENT_TYPES - {"other"} if types[t] >= 2) >= 9, types
    assert types["injury"] <= 14, "OSHA rewrites must not dominate"
    tags = Counter(t for i in items for t in i.get("tags", []))
    for tag in ("planned", "hedged", "negation", "correction", "ongoing", "relative_time"):
        assert tags[tag] >= 1, tag


def test_holdout_is_about_a_third(items):
    n = sum(i["split"] == "holdout" for i in items)
    assert 10 <= n <= 16


def test_no_blindcheck_words(items):
    words = REPO / "tools/blindcheck.words"
    patterns = [l.strip() for l in words.read_text(encoding="utf-8").splitlines() if l.strip() and not l.lstrip().startswith("#")]
    text = GOLD.read_text(encoding="utf-8")
    for p in patterns:
        assert not re.search(p, text, re.IGNORECASE), p


@pytest.fixture(scope="module")
def homophones():
    return [json.loads(l) for l in HOMOPHONES.read_text(encoding="utf-8").splitlines() if l.strip()]


def test_homophone_set_is_well_formed(homophones):
    test_every_slot_is_well_formed(homophones)
    test_missing_mandatory_matches_gold_for_single_turn_items(homophones)
    assert len(homophones) >= 8
    for it in homophones:
        assert it["source"] == "homophone"
        assert len(it["turns_misheard"]) == len(it["turns"]), it["id"]
        assert it["turns_misheard"] != it["turns"], it["id"]


def test_homophone_set_covers_the_requested_confusions(homophones):
    misheard = " ".join(t for it in homophones for t in it["turns_misheard"]).lower()
    for word in ("last weak", "miner", "fair alarm", "leek", "for people", "to chairs", "chilla"):
        assert word in misheard, word


def test_gold_uses_site_canonical_names_somewhere(items, homophones):
    text = json.dumps(items + homophones)
    for name in ("Seminar Hall 2", "Vindhya C4", "Himalaya 305", "Kohli Research Block", "Kadamba Mess",
                 "Yuktahar", "Bakul Nivas", "Parijaat", "Palash Nivas", "Aarogya Centre", "chiller"):
        assert name in text, name


def test_homophone_file_has_no_blindcheck_words():
    words = REPO / "tools/blindcheck.words"
    patterns = [l.strip() for l in words.read_text(encoding="utf-8").splitlines() if l.strip() and not l.lstrip().startswith("#")]
    text = HOMOPHONES.read_text(encoding="utf-8")
    for p in patterns:
        assert not re.search(p, text, re.IGNORECASE), p
