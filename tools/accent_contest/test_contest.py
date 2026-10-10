import json
import io
import tempfile
import unittest
import wave
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import contest
from contest import Clip, aggregate_wer, load_manifest, normalize, render_table, resolve_model, run_contest


class ContestTest(unittest.TestCase):
    def test_raw_export_preserves_text_and_keeps_existing_predictions(self):
        rows = [{"model": "stock", "clip_id": "one", "reference": "room two", "prediction": " Room 2! "}]
        exported = contest.raw_transcripts(rows)
        self.assertEqual(exported, [{"clip_id": "one", "model": "stock", "text": " Room 2! "}])
        self.assertEqual(rows[0]["prediction"], " Room 2! ")
        self.assertEqual(rows[0]["reference"], "room two")

    def test_metadata_selection_requires_exact_values_and_unique_keys(self):
        self.assertEqual(contest.parse_selection(["device=phone", "split=tuning", "language=en"]),
                         {"device": "phone", "split": "tuning", "language": "en"})
        for invalid in (["phone"], ["device="], ["=phone"], ["device=phone", "device=kit"]):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                contest.parse_selection(invalid)

    def test_manifest_selection_skips_unselected_missing_audio(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with wave.open(str(root / "audio.wav"), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(16000)
                audio.writeframes(b"\x00\x00")
            rows = [
                {"clip_id": "phone-dev", "wav": "audio.wav", "reference": "water leak", "device": "phone", "split": "tuning"},
                {"clip_id": "kit-dev", "wav": "not-downloaded.wav", "reference": "fire alarm", "device": "kit", "split": "tuning"},
                {"clip_id": "phone-test", "wav": "not-downloaded.wav", "reference": "water leak", "device": "phone", "split": "test"},
            ]
            manifest = root / "clips.jsonl"
            manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            selected = load_manifest(manifest, filters={"device": "phone", "split": "tuning"})
            self.assertEqual([clip.clip_id for clip in selected], ["phone-dev"])
            self.assertEqual(selected[0].reference, "water leak")
            with self.assertRaisesRegex(ValueError, "no clips"):
                load_manifest(manifest, filters={"device": "laptop"})
            with self.assertRaisesRegex(ValueError, "missing selection metadata.*language"):
                load_manifest(manifest, filters={"language": "en"})
            for invalid_device in (None, "", "  ", 123):
                rows[0]["device"] = invalid_device
                manifest.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
                with self.subTest(device=invalid_device), self.assertRaisesRegex(ValueError, "invalid selection metadata.*device"):
                    load_manifest(manifest, filters={"device": "phone", "split": "tuning"})

    def test_cli_rejects_overlapping_outputs_before_reading_or_inference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "clips.jsonl"
            output = root / "result.jsonl"
            manifest.write_text("preserve manifest")
            output.write_text("preserve output")
            for raw_path in (output, manifest):
                errors = io.StringIO()
                argv = ["contest.py", "--manifest", str(manifest), "--output", str(output),
                        "--raw-transcripts", str(raw_path)]
                with patch("sys.argv", argv), patch("sys.stderr", errors), self.assertRaises(SystemExit) as caught:
                    contest.main()
                self.assertEqual(caught.exception.code, 2)
                self.assertIn("output paths must be distinct", errors.getvalue())
                self.assertEqual(manifest.read_text(), "preserve manifest")
                self.assertEqual(output.read_text(), "preserve output")

    def test_cli_exports_raw_rows_and_records_selected_subset(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with wave.open(str(root / "audio.wav"), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(16000)
                audio.writeframes(b"\x00\x00")
            manifest = root / "clips.jsonl"
            manifest.write_text(json.dumps({"clip_id": "phone-1", "wav": "audio.wav", "reference": "water leak", "device": "phone"}) + "\n")
            calls = []

            def fake_pipeline(*args, **kwargs):
                def transcribe(wav):
                    calls.append(wav)
                    return {"text": "Water leak!"}
                return transcribe

            modules = {"torch": SimpleNamespace(cuda=SimpleNamespace(is_available=lambda: False)),
                       "transformers": SimpleNamespace(pipeline=fake_pipeline)}
            argv = ["contest.py", "--manifest", str(manifest), "--output", str(root / "wer.md"),
                    "--predictions", str(root / "predictions.jsonl"), "--raw-transcripts", str(root / "raw.jsonl"),
                    "--select", "device=phone", "--model", "test-model", "--dataset-label", "Synthetic CLI test"]
            with patch.dict("sys.modules", modules), patch("sys.argv", argv), patch("builtins.print"):
                contest.main()
            self.assertEqual(len(calls), 1)
            self.assertEqual(json.loads((root / "raw.jsonl").read_text()),
                             {"clip_id": "phone-1", "model": "test-model", "text": "Water leak!"})
            self.assertEqual(json.loads((root / "predictions.jsonl").read_text())["prediction"], "Water leak!")
            self.assertIn("device=phone", (root / "wer.md").read_text())
            self.assertIn("0/2", (root / "wer.md").read_text())

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
