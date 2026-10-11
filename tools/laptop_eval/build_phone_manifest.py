"""Manifest for the phone recordings, round 1 (Easy Voice Recorder, 16 kHz mono PCM16).

The recorders numbered speakers the other way round: their S1 read sheet S5, their S2 read sheet S4, S3 read S3,
S4 read S2, S5 read S1. Files are named `S<k>_<n>.wav` (no zero padding). Every clip under one sheet is one
voice, so calm/stressed pairs stay within a sheet; two people recorded the five sheets, so S1 to S5 are not
five distinct voices. Audio stays out of git; `wav` is the original file name, relative to the folder.

    python3 build_phone_manifest.py "<recordings folder>"
writes app/src/main/assets/eval/manifest_phone_r1.jsonl (all clips) and manifest_phone_r1_scripted.jsonl
(clips with an exact reference, the shape B's tools/accent_contest reads; copy it next to the WAVs).
"""

from __future__ import annotations

import argparse
import json
import re
import wave
from pathlib import Path

HERE = Path(__file__).resolve().parent
EVAL = HERE.parents[1] / "app/src/main/assets/eval"
TONE = {"stressed": "stressed"}
NOISE = {"noise": "fan", "alarm": "alarm"}
NOTE = {"alarm": "loud fire alarm about 1 m away; hard to follow even for people",
        "correction": "speaker changes one detail mid-sentence; gold is the card, check the final value said"}


def sheet_clip_id(name: str) -> str:
    m = re.fullmatch(r"S([1-5])_([1-8])\.wav", name)
    if not m:
        raise ValueError(f"not a round-1 phone file name: {name}")
    return f"S{6 - int(m.group(1))}_{int(m.group(2)):02d}"


def build(folder: Path) -> list[dict]:
    plan = {r["clip_id"]: r for r in (json.loads(l) for l in (EVAL / "manifest.jsonl").read_text(encoding="utf-8").splitlines() if l.strip())}
    split = {g["id"]: g["split"] for g in (json.loads(l) for l in (EVAL / "gold_v1.jsonl").read_text(encoding="utf-8").splitlines() if l.strip())}
    rows = []
    for wav in sorted(Path(folder).glob("*.wav")):
        clip_id = sheet_clip_id(wav.name)
        with wave.open(str(wav), "rb") as w:
            if (w.getframerate(), w.getnchannels(), w.getsampwidth()) != (16000, 1, 2):
                raise ValueError(f"expected 16 kHz mono PCM16: {wav.name}")
            duration = round(w.getnframes() / w.getframerate(), 3)
        p = plan[clip_id]
        notes = [NOTE[p["condition"]]] if p["condition"] in NOTE else []
        if clip_id.endswith("_08"):
            notes.append("single take: card and follow-up answers in one clip, not the incremental flow")
        rows.append({"clip_id": clip_id, "wav": wav.name, "reference": p["reference"], "gold_id": p["gold_id"],
                     "split": split[p["gold_id"]],
                     "device": "phone", "mic": "phone built-in", "speaker": p["speaker"], "mode": p["mode"],
                     "language": p["language"], "condition": p["condition"], "tone": TONE.get(p["condition"], "calm"),
                     "noise": NOISE.get(p["condition"], "quiet"), "captured_at": "", "duration_s": duration,
                     "label_source": "script" if p["reference"] else "unchecked", "note": "; ".join(notes)})
    return sorted(rows, key=lambda r: r["clip_id"])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder")
    args = ap.parse_args()
    rows = build(Path(args.folder))
    for name, subset in (("manifest_phone_r1.jsonl", rows), ("manifest_phone_r1_scripted.jsonl", [r for r in rows if r["reference"]])):
        (EVAL / name).write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in subset), encoding="utf-8")
        print(f"{name}: {len(subset)} clips")


if __name__ == "__main__":
    main()
