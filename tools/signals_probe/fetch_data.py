#!/usr/bin/env python3
"""Download the small public proxy clips used by the signals probe (never into the repo).

    python fetch_data.py esc50   OUT_DIR   # 75 ESC-50 clips (15 categories x 5), CC BY-NC, dev-only
    python fetch_data.py cremad  OUT_DIR   # 60 CREMA-D clips (6 emotions x 10 actors), ODbL / DbCL
    python fetch_data.py ravdess OUT_DIR   # 80 RAVDESS speech clips (8 emotions x 10 actors), CC BY-NC-SA 4.0

Only fixed GitHub URLs are used, file names are validated before a request, and nothing is executed.
"""
import csv
import io
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ESC50_CATEGORIES = [
    "siren", "clock_alarm", "crying_baby", "glass_breaking", "crackling_fire", "coughing", "sneezing",
    "door_wood_knock", "vacuum_cleaner", "water_drops", "pouring_water", "rain", "toilet_flush",
    "church_bells", "car_horn",
]
ESC50_RAW = "https://raw.githubusercontent.com/karolpiczak/ESC-50/master/"
CREMAD_RAW = "https://media.githubusercontent.com/media/CheyneyComputerScience/CREMA-D/master/AudioWAV/"
CREMAD_EMOTIONS = ["ANG", "NEU", "SAD", "FEA", "HAP", "DIS"]  # DIS is extra: SenseVoice has a DISGUSTED tag
RAVDESS_EMOTIONS = ["angry", "calm", "neutral", "sad", "fearful", "happy", "disgust", "surprised"]
HF_ROWS = "https://datasets-server.huggingface.co/rows"  # public HF mirror xbgoose/ravdess (parquet of the Zenodo speech set)
SAFE_NAME = re.compile(r"^[A-Za-z0-9_.-]+\.wav$")


def get(url: str, tries: int = 3) -> bytes:
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=180) as r:
                return r.read()
        except Exception as e:  # retry transient network errors, then fail loudly
            if i == tries - 1:
                raise
            print("retry", url[:80], e)


def fetch_esc50(out: Path, per_category: int = 5) -> None:
    out.mkdir(parents=True, exist_ok=True)
    meta = get(ESC50_RAW + "meta/esc50.csv").decode()
    (out / "esc50.csv").write_text(meta)
    rows = list(csv.DictReader(io.StringIO(meta)))
    for cat in ESC50_CATEGORIES:
        # first N clips of the category in meta-file order (sorted by file name), no cherry-picking
        picked = [r["filename"] for r in rows if r["category"] == cat][:per_category]
        for name in picked:
            assert SAFE_NAME.match(name), name
            dest = out / name
            if not dest.exists():
                dest.write_bytes(get(ESC50_RAW + "audio/" + name))
        print(f"{cat}: {len(picked)} clips")


def fetch_cremad(out: Path, actors: range = range(1001, 1011)) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for emo in CREMAD_EMOTIONS:
        for actor in actors:
            # sentence IEO ("It's eleven o'clock"), highest intensity for the emotions, XX for neutral
            name = f"{actor}_IEO_{emo}_{'XX' if emo == 'NEU' else 'HI'}.wav"
            assert SAFE_NAME.match(name), name
            dest = out / name
            if dest.exists():
                continue
            try:
                dest.write_bytes(get(CREMAD_RAW + name))
            except Exception as e:  # a missing file is reported, not hidden
                print("MISSING", name, e)
        print(emo, "done")


def fetch_ravdess(out: Path, per_emotion: int = 10) -> None:
    """One clip per actor (lowest actor numbers first): statement 'Kids are talking by the door', 1st repetition,
    strong intensity (neutral only exists at normal intensity). No cherry-picking beyond that rule.
    Rows are paged from the public HF datasets-server (the /filter endpoint was flaky, /rows is not)."""
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for offset in range(0, 1440, 100):
        url = HF_ROWS + "?" + urllib.parse.urlencode(
            {"dataset": "xbgoose/ravdess", "config": "default", "split": "train", "offset": offset, "length": 100})
        rows += json.loads(get(url).decode())["rows"]
    print(len(rows), "rows listed")
    for emo in RAVDESS_EMOTIONS:
        intensity = "normal" if emo == "neutral" else "strong"
        sel = [r for r in rows if r["row"]["emotion"] == emo and r["row"]["emotional_intensity"] == intensity
               and r["row"]["statement"] == "Kids are talking by the door"
               and r["row"]["repetition"] == "1st repetition"]
        sel.sort(key=lambda r: r["row"]["actor"])
        n = 0
        for r in sel[:per_emotion]:
            actor = int(r["row"]["actor"])
            dest = out / f"ravdess_{emo}_a{actor:02d}.wav"
            assert SAFE_NAME.match(dest.name), dest.name
            if not dest.exists():
                dest.write_bytes(get(r["row"]["audio"][0]["src"]))
            n += 1
        print(f"{emo}: {n} clips")


if __name__ == "__main__":
    kinds = {"esc50": fetch_esc50, "cremad": fetch_cremad, "ravdess": fetch_ravdess}
    if len(sys.argv) != 3 or sys.argv[1] not in kinds:
        sys.exit(__doc__)
    kinds[sys.argv[1]](Path(sys.argv[2]))
