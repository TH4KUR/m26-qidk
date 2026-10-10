"""The recording plan: 5 speakers x 8 clips, every clip tied to a gold item, conditions spread."""

from collections import Counter

from build_sheets import plan_clips


def test_forty_core_clips_eight_per_speaker():
    core = [c for c in plan_clips() if c["language"] == "en"]
    assert len(core) == 40
    assert Counter(c["speaker"] for c in core) == {f"S{i}": 8 for i in range(1, 6)}


def test_each_speaker_reads_deck_calm_and_stressed():
    core = [c for c in plan_clips() if c["language"] == "en"]
    for s in range(1, 6):
        deck = sorted(c["condition"] for c in core if c["speaker"] == f"S{s}" and c["gold_id"] == "g01")
        assert deck == ["clean", "stressed"]


def test_modes_and_conditions_cover_the_handoff():
    core = [c for c in plan_clips() if c["language"] == "en"]
    assert Counter(c["mode"] for c in core) == {"scripted": 20, "prompted": 20}
    cond = Counter(c["condition"] for c in core)
    assert cond["alarm"] >= 3 and cond["noise"] >= 3 and cond["stressed"] >= 5
    corrections = sum(c["condition"] == "correction" or "correction" in c["tags"] for c in core)
    assert corrections >= 6


def test_scripted_clips_carry_the_exact_reference():
    for c in plan_clips():
        if c["mode"] == "scripted":
            assert c["reference"] and c["reference"] == " ".join(c["script"])
        else:
            assert c["reference"] == ""  # typed by ear after recording


def test_indic_extras_exist():
    langs = Counter(c["language"] for c in plan_clips())
    assert langs["hi"] >= 6 and langs["te"] >= 4


def test_clip_ids_unique():
    ids = [c["clip_id"] for c in plan_clips()]
    assert len(ids) == len(set(ids))
