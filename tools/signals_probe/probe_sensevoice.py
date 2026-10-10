#!/usr/bin/env python3
"""Run sherpa-onnx SenseVoice (int8) on wav files and print the RAW result fields and a confusion table.

Run with the venv python and -I (isolated mode), because it reads downloaded files:

    ~/.venvs/megathon-signals/bin/python -I probe_sensevoice.py MODEL_DIR \
        --wavs DIR_WITH_TEST_WAVS [--wavs ...] --out OUT.jsonl [--threads 2] [--language auto]

MODEL_DIR holds model.int8.onnx and tokens.txt (sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17).
Each --wavs directory is scanned for *.wav. A CREMA-D style name (1001_IEO_ANG_HI.wav) gives a true
emotion label from its third field; other files are listed as 'unlabelled'. Everything is resampled to
16 kHz mono. One JSON line per clip goes to --out with the repr() of every field the result exposes.
"""
import argparse
import collections
import csv
import json
import re
import sys
import time
from math import gcd
from pathlib import Path

import numpy as np
import sherpa_onnx
import soundfile as sf
from scipy.signal import resample_poly

CREMAD = {"ANG": "angry", "NEU": "neutral", "SAD": "sad", "FEA": "fearful", "HAP": "happy", "DIS": "disgust"}


def load_16k_mono(path: Path) -> np.ndarray:
    x, sr = sf.read(str(path), dtype="float32", always_2d=True)
    x = x.mean(axis=1)
    if sr != 16000:
        g = gcd(sr, 16000)
        x = resample_poly(x, 16000 // g, sr // g).astype(np.float32)
    return np.ascontiguousarray(x, dtype=np.float32)


META: dict = {}  # file name -> ESC-50 category, filled from --esc50-meta
ORDER = ["angry", "neutral", "calm", "sad", "fearful", "happy", "disgust", "surprised"]


def true_label(path: Path) -> str:
    if path.name in META:
        return "esc50:" + META[path.name]
    parts = path.stem.split("_")
    if len(parts) == 3 and parts[0] == "ravdess" and parts[1] in ORDER:
        return parts[1]
    if len(parts) >= 3 and re.fullmatch(r"\d{4}", parts[0]) and parts[2] in CREMAD:
        return CREMAD[parts[2]]
    return "unlabelled"


def result_fields(r) -> dict:
    """repr() of every public attribute the result object exposes, exactly as returned."""
    out = {}
    for name in dir(r):
        if name.startswith("_"):
            continue
        try:
            v = getattr(r, name)
        except Exception as e:  # report, do not hide
            out[name] = f"<error {e!r}>"
            continue
        if callable(v):
            continue
        out[name] = repr(v)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model_dir", type=Path)
    ap.add_argument("--wavs", type=Path, action="append", required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--threads", type=int, default=2)
    ap.add_argument("--language", default="auto", help="auto, zh, en, ja, ko, yue")
    ap.add_argument("--no-itn", action="store_true")
    ap.add_argument("--esc50-meta", type=Path, help="esc50.csv: label ESC-50 clips so sound-event tags can be tabulated")
    a = ap.parse_args()
    if a.esc50_meta:
        with a.esc50_meta.open(newline="") as f:
            META.update({r["filename"]: r["category"] for r in csv.DictReader(f)})

    rec = sherpa_onnx.OfflineRecognizer.from_sense_voice(
        model=str(a.model_dir / "model.int8.onnx"),
        tokens=str(a.model_dir / "tokens.txt"),
        num_threads=a.threads,
        use_itn=not a.no_itn,
        language="" if a.language == "auto" else a.language,
        debug=False,
    )
    print("sherpa_onnx", getattr(sherpa_onnx, "__version__", "?"), "use_itn", not a.no_itn, "language", a.language)

    rows = []
    for d in a.wavs:
        for p in sorted(d.glob("*.wav")):
            audio = load_16k_mono(p)
            s = rec.create_stream()
            s.accept_waveform(16000, audio)
            t0 = time.time()
            rec.decode_stream(s)
            dt = time.time() - t0
            r = s.result
            fields = result_fields(r)
            row = {
                "file": p.name,
                "dir": str(d.name),
                "seconds": round(len(audio) / 16000, 2),
                "decode_s": round(dt, 2),
                "true": true_label(p),
                "text": r.text,
                "lang": getattr(r, "lang", None),
                "emotion": getattr(r, "emotion", None),
                "event": getattr(r, "event", None),
                "fields_repr": fields,
            }
            rows.append(row)
            print(f"{p.name}: text={r.text!r} lang={getattr(r, 'lang', None)!r} "
                  f"emotion={getattr(r, 'emotion', None)!r} event={getattr(r, 'event', None)!r}")
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with a.out.open("w") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    if rows:
        print("\nfields exposed by the result object:", sorted(rows[0]["fields_repr"]))
    sound = [r for r in rows if r["true"].startswith("esc50:")]
    if sound:
        print(f"\n=== Sound-event and emotion tags on ESC-50 clips (n={len(sound)}; most are not speech) ===")
        for cat in sorted({r["true"] for r in sound}):
            sub = [r for r in sound if r["true"] == cat]
            print(f"  {cat[6:]:<16} n={len(sub)} event={dict(collections.Counter(r['event'] for r in sub))} "
                  f"emotion={dict(collections.Counter(r['emotion'] for r in sub))}")
    labelled = [r for r in rows if r["true"] != "unlabelled" and not r["true"].startswith("esc50:")]
    for dname in (sorted({r["dir"] for r in labelled}) + ["ALL"]) if labelled else []:
        report([r for r in labelled if dname == "ALL" or r["dir"] == dname], dname)
    print("Mean decode seconds per clip:", round(float(np.mean([r["decode_s"] for r in rows])), 2))


def report(rows: list, title: str) -> None:
    tags = sorted({r["emotion"] for r in rows}, key=str)
    print(f"\n=== Confusion [{title}]: true label x raw SenseVoice emotion string, n={len(rows)} acted clips ===")
    print("true".ljust(10) + "".join(str(t).rjust(18) for t in tags) + "  | n")
    totals = collections.Counter()
    for lab in ORDER:
        sub = [r for r in rows if r["true"] == lab]
        if not sub:
            continue
        c = collections.Counter(r["emotion"] for r in sub)
        totals.update(c)
        print(lab.ljust(10) + "".join(f"{c[t]}/{len(sub)}".rjust(18) for t in tags) + f"  | {len(sub)}")
    print("all".ljust(10) + "".join(f"{totals[t]}/{len(rows)}".rjust(18) for t in tags))
    print("Event tags:", {k: f"{v}/{len(rows)}" for k, v in collections.Counter(r["event"] for r in rows).most_common()})
    print("Event tags by true label:")
    for lab in ORDER:
        sub = [r for r in rows if r["true"] == lab]
        if sub:
            print(f"  {lab}: {dict(collections.Counter(r['event'] for r in sub))}")
    print("Language tags:", dict(collections.Counter(r["lang"] for r in rows)))
    print("Transcripts:", dict(collections.Counter(r["text"] for r in rows).most_common(6)))


if __name__ == "__main__":
    main()
