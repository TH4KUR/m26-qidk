"""Pick a seeded random subset of FLEURS test clips for Hindi and Telugu and write a manifest in accent_contest's shape.

FLEURS (google/fleurs, CC-BY-4.0) is read speech of sentences, not incident speech: results from this subset are an experimental
ranking aid, never a claim about incident speech. Audio stays outside the repo.

  python3 tools/fleurs_subset/prepare.py --fleurs-dir ~/megathon-data/fleurs --out-dir ~/megathon-data/fleurs_subset \\
      --revision 70bb2e84b976b7e960aa89f1c648e09c59f894dd
"""
import argparse
import array
import csv
import json
import random
import struct
import tarfile
from dataclasses import dataclass
from pathlib import Path

LICENSE = "CC-BY-4.0"
LANGUAGES = (("hi", "hi_in"), ("te", "te_in"))
DEFAULT_SEED = 20261011


@dataclass(frozen=True)
class Row:
    id: str
    file: str
    raw: str
    normalized: str
    gender: str


def read_tsv(path: Path) -> list[Row]:
    """FLEURS test.tsv columns: id, file name, raw transcription, normalised transcription, characters, num samples, gender."""
    seen, rows = set(), []
    with open(path, encoding="utf-8", newline="") as handle:
        for line in csv.reader(handle, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(line) < 4 or not line[1].strip():
                continue
            if line[1] in seen:
                continue
            seen.add(line[1])
            rows.append(Row(line[0], line[1], line[2], line[3], line[6] if len(line) > 6 else ""))
    return rows


def choose(rows: list[Row], n: int, seed: int, language: str) -> list[Row]:
    """A random sample, not hand-picked: the same seed and language always give the same clips; each language has its own stream."""
    if len(rows) < n:
        raise ValueError(f"only {len(rows)} clips available, {n} requested")
    return sorted(random.Random(f"{seed}:{language}").sample(rows, n), key=lambda row: row.file)


def to_pcm16_wav(data: bytes) -> bytes:
    """16 kHz mono WAV as PCM16. FLEURS ships 32-bit float WAVs (format tag 3), which Python's `wave` cannot read, so the RIFF
    chunks are read directly: float samples are scaled by 32767 and clipped (never wrapped). Anything that is not mono 16 kHz is an error."""
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("not a RIFF/WAVE file")
    fmt = pcm = None
    at = 12
    while at + 8 <= len(data):
        tag, size = data[at:at + 4], struct.unpack("<I", data[at + 4:at + 8])[0]
        body = data[at + 8:at + 8 + size]
        if tag == b"fmt ":
            fmt = struct.unpack("<HHIIHH", body[:16])
        elif tag == b"data":
            pcm = body
        at += 8 + size + (size & 1)
    if fmt is None or pcm is None:
        raise ValueError("WAV without fmt or data chunk")
    format_tag, channels, rate, _, _, bits = fmt
    if channels != 1 or rate != 16000:
        raise ValueError(f"expected mono 16 kHz, got {channels} channel(s) at {rate} Hz")
    if format_tag == 1 and bits == 16:
        samples = array.array("h", pcm[: len(pcm) // 2 * 2])
    elif format_tag == 3 and bits == 32:
        floats = array.array("f", pcm[: len(pcm) // 4 * 4])
        samples = array.array("h", (max(-32767, min(32767, int(x * 32767))) for x in floats))
    else:
        raise ValueError(f"unsupported WAV format tag {format_tag} with {bits} bits")
    header = (b"RIFF" + struct.pack("<I", 36 + samples.itemsize * len(samples)) + b"WAVEfmt " + struct.pack("<I", 16)
              + struct.pack("<HHIIHH", 1, 1, 16000, 32000, 2, 16) + b"data" + struct.pack("<I", samples.itemsize * len(samples)))
    return header + samples.tobytes()


def extract(tar_path: Path, wanted: set[str], out_dir: Path) -> None:
    """Write only the wanted WAVs (matched by file name) to out_dir as 16 kHz mono PCM16; every one must be present."""
    out_dir.mkdir(parents=True, exist_ok=True)
    found: set[str] = set()
    with tarfile.open(tar_path, "r:gz") as tar:
        for member in tar:
            name = Path(member.name).name
            if not member.isfile() or name not in wanted or name in found:
                continue
            try:
                data = to_pcm16_wav(tar.extractfile(member).read())
            except ValueError as error:
                raise ValueError(f"{name}: {error}") from error
            (out_dir / name).write_bytes(data)
            found.add(name)
            if found == wanted:
                break
    if found != wanted:
        raise ValueError(f"not in the archive: {sorted(wanted - found)}")


def write_manifest(path: Path, groups: list[tuple[str, str, list[Row]]], revision: str, seed: int) -> None:
    with open(path, "w", encoding="utf-8") as out:
        for short, directory, rows in groups:
            for index, row in enumerate(rows, start=1):
                out.write(json.dumps({
                    "clip_id": f"fleurs-{short}-{index:02d}",
                    "wav": f"{directory}/{row.file}",
                    "reference": row.raw,
                    "reference_fleurs_normalized": row.normalized,
                    "language": short,
                    "source": "FLEURS (google/fleurs)",
                    "license": LICENSE,
                    "dataset_revision": revision,
                    "split": "test",
                    "seed": seed,
                    "fleurs_id": row.id,
                    "gender": row.gender,
                    "label_source": "dataset",
                    "note": "experimental, small n, FLEURS read speech, not incident speech",
                }, ensure_ascii=False) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--fleurs-dir", type=Path, required=True, help="folder holding data/<lang>/test.tsv and data/<lang>/audio/test.tar.gz")
    parser.add_argument("--out-dir", type=Path, required=True, help="outside the repo; receives the WAVs, manifest_fleurs_r1.jsonl and selection_log.json")
    parser.add_argument("--revision", required=True, help="dataset revision the files came from (recorded in the manifest and log)")
    parser.add_argument("--per-language", type=int, default=20)
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    args = parser.parse_args()
    groups, log = [], {"dataset": "google/fleurs", "revision": args.revision, "license": LICENSE, "split": "test", "seed": args.seed,
                       "per_language": args.per_language, "languages": {}}
    for short, directory in LANGUAGES:
        base = args.fleurs_dir / "data" / directory
        rows = read_tsv(base / "test.tsv")
        picked = choose(rows, args.per_language, args.seed, directory)
        extract(base / "audio" / "test.tar.gz", {row.file for row in picked}, args.out_dir / directory)
        groups.append((short, directory, picked))
        log["languages"][directory] = {"available": len(rows), "chosen": [row.file for row in picked]}
    write_manifest(args.out_dir / "manifest_fleurs_r1.jsonl", groups, args.revision, args.seed)
    (args.out_dir / "selection_log.json").write_text(json.dumps(log, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {sum(len(g[2]) for g in groups)} clips to {args.out_dir}")


if __name__ == "__main__":
    main()
