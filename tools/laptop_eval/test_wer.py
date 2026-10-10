"""WER/CER for the Judge Bench audio runs. Normalisation and edit distance match B's accent contest
(tools/accent_contest/contest.py), so the two tables agree."""

import json

import pytest

from wer import aggregate, cer, normalize, wer_counts


def test_normalization_matches_b():
    assert normalize("Conference Room 2, at 10 AM!") == ["conference", "room", "2", "at", "10", "am"]


def test_aggregate_uses_total_reference_words_like_b():
    pairs = [("water leak near room two", "water leak near room 2"), ("fire alarm", "fire")]
    r = aggregate(pairs)
    assert (r["errors"], r["reference_words"]) == (2, 7)
    assert r["wer"] == pytest.approx(2 / 7)


def test_empty_hypothesis_is_all_deletions():
    assert wer_counts("fire alarm", "") == (2, 2)


def test_cer_counts_characters_without_spaces():
    assert cer("पानी आ रहा है", "पानी आ रहा") == pytest.approx(2 / 10)


def test_manifest_and_transcripts_join_on_clip_id(tmp_path):
    from wer import score_transcripts
    man = tmp_path / "manifest.jsonl"
    man.write_text("\n".join(json.dumps(r) for r in [
        {"clip_id": "S1_01", "reference": "water leak near room two", "device": "kit", "language": "en", "condition": "clean"},
        {"clip_id": "S1_02", "reference": "fire alarm", "device": "phone", "language": "en", "condition": "noise"},
        {"clip_id": "S1_03", "reference": "", "device": "kit", "language": "en", "condition": "clean"}]))
    hyp = {"S1_01": "water leak near room 2", "S1_02": "fire"}
    out = score_transcripts(man, hyp)
    assert out["all"]["errors"] == 2 and out["all"]["reference_words"] == 7
    assert out["by_device"]["kit"]["clips"] == 1          # clip without a typed reference is skipped
    assert out["by_condition"]["noise"]["errors"] == 1
    assert out["skipped_no_reference"] == ["S1_03"]
