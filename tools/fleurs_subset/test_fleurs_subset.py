import io
import json
import sys
import tarfile
import tempfile
import unittest
import wave
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "accent_contest"))

import contest  # noqa: E402  (the English contest's manifest loader: the manifest must load there too)
import indic_wer  # noqa: E402
import prepare  # noqa: E402


def wav_bytes(frames=160, rate=16000):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(b"\x01\x00" * frames)
    return buffer.getvalue()


def float_wav_bytes(samples, rate=16000, channels=1):
    """A WAVE_FORMAT_IEEE_FLOAT (tag 3) file, which is what FLEURS ships."""
    import array
    import struct
    data = array.array("f", samples).tobytes()
    fmt = struct.pack("<HHIIHH", 3, channels, rate, rate * channels * 4, channels * 4, 32)
    return b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " + struct.pack("<I", 16) + fmt + b"data" + struct.pack("<I", len(data)) + data


def make_tsv(path, names):
    rows = [f"{i}\t{name}\tनमस्ते दुनिया {i}।\tनमस्ते दुनिया {i}\tन म स ् त े\t16000\tFEMALE" for i, name in enumerate(names)]
    path.write_text("\n".join(rows) + "\n", encoding="utf-8")


def make_tar(path, names, rate=16000, payload=None):
    with tarfile.open(path, "w:gz") as tar:
        for name in names:
            data = payload if payload is not None else wav_bytes(rate=rate)
            info = tarfile.TarInfo(f"test/{name}")
            info.size = len(data)
            tar.addfile(info, io.BytesIO(data))


class IndicNormalizeTest(unittest.TestCase):
    def test_vowel_signs_and_virama_survive(self):
        self.assertEqual(indic_wer.normalize("स्कीइंग मार्ग"), ["स्कीइंग", "मार्ग"])
        self.assertEqual(indic_wer.normalize("తెలుగు భాష అందమైనది"), ["తెలుగు", "భాష", "అందమైనది"])

    def test_punctuation_including_the_danda_is_dropped_and_case_folded(self):
        self.assertEqual(indic_wer.normalize("नमस्ते, दुनिया। OK!"), ["नमस्ते", "दुनिया", "ok"])

    def test_wer_counts_whole_words(self):
        self.assertEqual(indic_wer.wer("आग लगी है यहाँ", "आग लगी है यहां"), (1, 4))
        self.assertEqual(indic_wer.wer("आग लगी है", "आग लगी है"), (0, 3))

    def test_cer_sees_a_single_wrong_vowel_sign_that_wer_hides_in_one_word(self):
        errors, total = indic_wer.cer("मार्ग", "मारग")
        self.assertEqual(total, len("मार्ग"))
        self.assertGreater(errors, 0)


class SelectionTest(unittest.TestCase):
    def test_same_seed_same_clips_and_each_language_is_independent(self):
        with tempfile.TemporaryDirectory() as d:
            tsv = Path(d) / "test.tsv"
            make_tsv(tsv, [f"clip{i}.wav" for i in range(100)])
            rows = prepare.read_tsv(tsv)
            a = prepare.choose(rows, 20, seed=7, language="hi_in")
            self.assertEqual([r.file for r in a], [r.file for r in prepare.choose(rows, 20, seed=7, language="hi_in")])
            self.assertNotEqual([r.file for r in a], [r.file for r in prepare.choose(rows, 20, seed=7, language="te_in")])
            self.assertEqual(20, len({r.file for r in a}))

    def test_duplicate_file_names_are_not_double_counted_and_short_files_fail_loudly(self):
        with tempfile.TemporaryDirectory() as d:
            tsv = Path(d) / "test.tsv"
            make_tsv(tsv, ["a.wav", "a.wav", "b.wav"])
            self.assertEqual(2, len(prepare.read_tsv(tsv)))
            with self.assertRaises(ValueError):
                prepare.choose(prepare.read_tsv(tsv), 5, seed=1, language="hi_in")


class ExtractAndManifestTest(unittest.TestCase):
    def test_only_the_chosen_clips_are_extracted_and_the_manifest_loads_in_the_contest_loader(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            names = [f"clip{i}.wav" for i in range(30)]
            make_tsv(root / "test.tsv", names)
            make_tar(root / "test.tar.gz", names)
            rows = prepare.choose(prepare.read_tsv(root / "test.tsv"), 5, seed=3, language="hi_in")
            out = root / "out"
            prepare.extract(root / "test.tar.gz", {r.file for r in rows}, out / "hi_in")
            self.assertEqual(sorted(p.name for p in (out / "hi_in").iterdir()), sorted(r.file for r in rows))
            manifest = out / "manifest.jsonl"
            prepare.write_manifest(manifest, [("hi", "hi_in", rows)], revision="abc123", seed=3)
            first = json.loads(manifest.read_text().splitlines()[0])
            for key in ("clip_id", "wav", "reference", "language", "source", "license", "dataset_revision", "split", "seed", "label_source"):
                self.assertIn(key, first)
            self.assertEqual("CC-BY-4.0", first["license"])
            self.assertEqual(5, len(contest.load_manifest(manifest)))

    def test_a_clip_missing_from_the_archive_is_an_error(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            make_tar(root / "test.tar.gz", ["a.wav"])
            with self.assertRaises(ValueError):
                prepare.extract(root / "test.tar.gz", {"a.wav", "missing.wav"}, root / "out")

    def test_float32_wavs_as_shipped_by_fleurs_are_converted_to_pcm16(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            make_tar(root / "test.tar.gz", ["a.wav"], payload=float_wav_bytes([0.0, 0.5, -0.5, 1.0, -1.0, 2.0]))
            prepare.extract(root / "test.tar.gz", {"a.wav"}, root / "out")
            with wave.open(str(root / "out" / "a.wav")) as w:
                self.assertEqual((1, 16000, 2, "NONE"), (w.getnchannels(), w.getframerate(), w.getsampwidth(), w.getcomptype()))
                frames = w.readframes(w.getnframes())
            import array
            samples = array.array("h", frames)
            self.assertEqual([0, 16383, -16383, 32767, -32767, 32767], list(samples), "scaled by 32767 and clipped, never wrapped")

    def test_stereo_or_other_rates_are_an_error_not_a_crash(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            make_tar(root / "test.tar.gz", ["a.wav"], payload=float_wav_bytes([0.0, 0.1], channels=2))
            with self.assertRaises(ValueError):
                prepare.extract(root / "test.tar.gz", {"a.wav"}, root / "out")

    def test_wrong_audio_format_is_reported_not_silently_accepted(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            make_tar(root / "test.tar.gz", ["a.wav"], rate=44100)
            with self.assertRaises(ValueError):
                prepare.extract(root / "test.tar.gz", {"a.wav"}, root / "out")


if __name__ == "__main__":
    unittest.main()
