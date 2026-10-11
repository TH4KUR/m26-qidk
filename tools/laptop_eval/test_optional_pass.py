"""Second-pass helper for the optional fields: filters, the experiment-only fair match, and the input loaders."""

from optional_pass import fair, keep_equipment, keep_name, read_transcripts


def test_equipment_filter_drops_people_and_incident_words_only():
    assert keep_equipment("water pipe") and keep_equipment("3D printer") and keep_equipment("bulk bags")
    assert not keep_equipment("water leak") and not keep_equipment("wet floor")
    assert not keep_equipment("patient") and not keep_equipment("delivery boy") and not keep_equipment("someone")


def test_name_filter_keeps_capitalised_names_and_drops_roles():
    assert keep_name("Kavya") and keep_name("Mohan Rao")
    assert not keep_name("me") and not keep_name("security guard") and not keep_name("Guard")


def test_fair_match_separates_extra_words_from_wrong_values():
    gold = {"status": "REPORTED", "value": "vending machine"}
    assert fair("equipment_asset", gold, "vending machine") == "exact"
    assert fair("equipment_asset", gold, "glass door of the vending machine") == "contains"
    assert fair("equipment_asset", {"status": "REPORTED", "value": "parked car"}, "tree branch") == "wrong"
    assert fair("immediate_actions", {"status": "REPORTED", "value": "calling the ambulance"}, "called the ambulance now") == "contains"
    assert fair("immediate_actions", {"status": "REPORTED", "value": "put a cone next to it and informed the estate office"},
                "put a cone next to it") == "partial"


def test_transcripts_sheet_gives_clip_ids_and_skips_blank_or_held_out(tmp_path):
    sheet = tmp_path / "t.md"
    sheet.write_text(
        "# Transcripts\n\n## 1. S1_5.wav   (clip S5_05, clean)\nCard:\n- What: x\n\nSAID: there was a parked car\nSKIPPED: none\nNOTES: \n\n"
        "## 2. S1_8.wav   (clip S5_08, clean, held out)\nSAID: held out words\n\n"
        "## 3. S4_6.wav   (clip S2_06, alarm)\nSAID: \n", encoding="utf-8")
    assert read_transcripts(sheet) == [("S5_05", "there was a parked car")]
