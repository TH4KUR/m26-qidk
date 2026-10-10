import json
import tempfile
import unittest
import wave
from pathlib import Path

from contest import Clip, aggregate_wer, load_manifest, normalize, render_table, resolve_model, run_contest


class ContestTest(unittest.TestCase):
    def test_normalization_ignores_case_and_punctuation(self):
        self.assertEqual(normalize("Conference Room 2, at 10 AM!"), ["conference", "room", "2", "at", "10", "am"])

    def test_aggregate_uses_total_reference_words(self):
        clips = [Clip("one", Path("one.wav"), "water leak near room two"), Clip("two", Path("two.wav"), "fire alarm")]
        predictions = {"one": "water leak near room 2", "two": "fire"}
        result = aggregate_wer(clips, predictions)
        self.assertEqual(result["errors"], 2)
        self.assertEqual(result["reference_words"], 7)
        self.assertAlmostEqual(result["wer"], 2 / 7)

    def test_manifest_rejects_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with wave.open(str(root / "audio.wav"), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(16000)
                audio.writeframes(b"\x00\x00")
            manifest = root / "clips.jsonl"
            row = {"clip_id": "x", "wav": "audio.wav", "reference": "water leak"}
            manifest.write_text(json.dumps(row) + "\n" + json.dumps(row) + "\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate clip_id"):
                load_manifest(manifest)

    def test_contest_gives_every_model_the_same_clips(self):
        clips = [Clip("a", Path("a.wav"), "water leak"), Clip("b", Path("b.wav"), "fire alarm")]
        calls = []

        def transcribe(model, wav):
            calls.append((model, wav.name))
            return "water leak" if wav.name == "a.wav" else "fire alarm"

        rows, predictions = run_contest(clips, ["stock", "adapted"], transcribe)
        self.assertEqual(calls, [("stock", "a.wav"), ("stock", "b.wav"), ("adapted", "a.wav"), ("adapted", "b.wav")])
        self.assertTrue(all(row["wer"] == 0 for row in rows))
        self.assertEqual(len(predictions), 4)

    def test_table_is_ranked_and_shows_denominators(self):
        rows = [
            {"model": "worse", "errors": 2, "reference_words": 10, "clips": 2, "wer": 0.2},
            {"model": "better", "errors": 1, "reference_words": 10, "clips": 2, "wer": 0.1},
        ]
        table = render_table(rows)
        self.assertLess(table.index("better"), table.index("worse"))
        self.assertIn("1/10", table)
        self.assertIn("10.00%", table)

    def test_local_model_directory_keeps_repository_name_in_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "whisper-tiny.en").mkdir()
            (root / "whisper-tiny.en" / "model.safetensors").touch()
            self.assertEqual(
                resolve_model("openai/whisper-tiny.en", root),
                str(root / "whisper-tiny.en"),
            )
            with self.assertRaisesRegex(ValueError, "model directory missing"):
                resolve_model("openai/whisper-base.en", root)

    def test_incomplete_local_model_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "whisper-tiny.en").mkdir()
            with self.assertRaisesRegex(ValueError, "model weights missing"):
                resolve_model("openai/whisper-tiny.en", root)


if __name__ == "__main__":
    unittest.main()
