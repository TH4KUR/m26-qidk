"""Does a quiet microphone cost accuracy, and does peak-normalising each clip help?

The kit's mic was measured quiet (2026-10-10: room floor about -45 dBFS, speech peaks -35 to -30 dBFS). This script
takes the clips of a contest manifest and transcribes three versions of each with the same models:

  original   the clip as recorded
  quiet      the clip scaled so its loud 20 ms frames sit at -32 dBFS RMS, plus white noise at -45 dBFS RMS
  quiet+norm the quiet version, then peak-normalised to -3 dBFS (gain capped at 30 dB)

White noise is a crude stand-in for a real room, and the simulation says nothing about the kit's actual
frequency response or AGC, so the result is a hint to check on kit recordings, not a measurement of the kit.
"""

from __future__ import annotations

import argparse
import json
import sys
import wave
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from contest import aggregate_wer, load_manifest, resolve_model  # noqa: E402

RATE = 16_000
FRAME = RATE // 50  # 20 ms
LOUD_TARGET_DBFS = -32.0
NOISE_DBFS = -45.0
PEAK_TARGET_DBFS = -3.0
MAX_GAIN_DB = 30.0


def dbfs_to_rms(db: float) -> float:
    return 10 ** (db / 20)


def read_wav(path: Path) -> np.ndarray:
    with wave.open(str(path), "rb") as audio:
        return np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").astype(np.float32) / 32768.0


def loud_frame_rms(signal: np.ndarray) -> float:
    frames = signal[: len(signal) // FRAME * FRAME].reshape(-1, FRAME)
    rms = np.sqrt((frames**2).mean(axis=1))
    return float(np.percentile(rms, 90)) or 1e-6


def make_quiet(signal: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    scaled = signal * (dbfs_to_rms(LOUD_TARGET_DBFS) / loud_frame_rms(signal))
    noisy = scaled + rng.normal(0.0, dbfs_to_rms(NOISE_DBFS), size=scaled.shape).astype(np.float32)
    return np.clip(np.round(noisy * 32768.0), -32768, 32767).astype(np.float32) / 32768.0  # quantise like PCM16


def peak_normalise(signal: np.ndarray) -> np.ndarray:
    peak = float(np.abs(signal).max()) or 1e-6
    gain = min(dbfs_to_rms(PEAK_TARGET_DBFS) / peak, dbfs_to_rms(MAX_GAIN_DB))
    return np.clip(signal * gain, -1.0, 1.0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--models-dir", type=Path, required=True)
    parser.add_argument("--model", action="append", dest="models", required=True)
    parser.add_argument("--output", type=Path, required=True, help="JSON results file")
    parser.add_argument("--seed", type=int, default=20261011)
    args = parser.parse_args()

    import torch
    from transformers import pipeline

    clips = load_manifest(args.manifest)
    rng = np.random.default_rng(args.seed)
    variants = {"original": [], "quiet": [], "quiet+norm": []}
    for clip in clips:
        original = read_wav(clip.wav)
        quiet = make_quiet(original, rng)
        variants["original"].append(original)
        variants["quiet"].append(quiet)
        variants["quiet+norm"].append(peak_normalise(quiet))

    results = []
    for model in args.models:
        asr = pipeline(
            "automatic-speech-recognition",
            model=resolve_model(model, args.models_dir),
            device=0 if torch.cuda.is_available() else -1,
        )
        for name, signals in variants.items():
            predictions = {
                clip.clip_id: asr({"raw": signal, "sampling_rate": RATE})["text"].strip()
                for clip, signal in zip(clips, signals)
            }
            row = {"model": model, "variant": name, **aggregate_wer(clips, predictions)}
            results.append(row)
            print(f"{model:32s} {name:11s} {row['wer']:.2%} ({row['errors']}/{row['reference_words']})", flush=True)
        del asr
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
