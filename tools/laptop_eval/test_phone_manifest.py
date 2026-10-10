"""Phone round 1: the team's file names map to sheet clips (their S1 read sheet S5, ..., their S5 read sheet S1)."""

import wave

import pytest

from build_phone_manifest import build, sheet_clip_id


def test_file_speakers_are_reversed_onto_the_sheets():
    assert sheet_clip_id("S1_3.wav") == "S5_03"
    assert sheet_clip_id("S2_1.wav") == "S4_01"
    assert sheet_clip_id("S3_8.wav") == "S3_08"
    assert sheet_clip_id("S5_6.wav") == "S1_06"


def test_unknown_names_are_rejected():
    with pytest.raises(ValueError):
        sheet_clip_id("recording_001.wav")


def _wav(path, seconds=0.5):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(b"\0\0" * int(16000 * seconds))


def test_rows_inherit_the_plan_and_keep_the_original_file(tmp_path):
    for name in ("S1_3.wav", "S1_6.wav", "S1_8.wav"):
        _wav(tmp_path / name)
    rows = {r["clip_id"]: r for r in build(tmp_path)}
    scripted = rows["S5_03"]
    assert scripted["wav"] == "S1_3.wav" and scripted["gold_id"] == "g27"
    assert scripted["reference"] and scripted["label_source"] == "script"
    assert scripted["device"] == "phone" and scripted["duration_s"] == 0.5
    alarm = rows["S5_06"]
    assert alarm["reference"] == "" and alarm["label_source"] == "unchecked"
    assert alarm["noise"] == "alarm" and "alarm" in alarm["note"]
    assert "single take" in rows["S5_08"]["note"]
