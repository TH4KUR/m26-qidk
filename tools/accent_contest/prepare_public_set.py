"""Build a contest manifest (clips.jsonl + 16 kHz mono PCM16 WAVs) from a public Hugging Face speech dataset.

Built for parquet datasets that store audio inline (a struct with a `bytes` field) and are sorted by
speaker, such as WillHeld/india_accent_cv (Mozilla Common Voice, India-and-South-Asia accent group).
Nothing is downloaded in full: the script first reads only the parquet footers (speaker min/max per row group), then
fetches just one row group (about 100 clips, ~17 MB) per chosen speaker.

Selection is deterministic for a fixed (--seed, dataset revision, parameters):
  1. list every (shard, row group) and the speakers it contains;
  2. shuffle the speakers with random.Random(seed) and walk that order;
  3. for each speaker visit its row groups in a seeded shuffled order, keep rows that pass the
     --where filters and the word-count limits, decode, and keep clips whose duration is within
     [--min-seconds, --max-seconds]; take up to --per-speaker clips (seeded shuffle) per speaker;
  4. stop at --n clips.

The reference is the dataset transcript as-is (contest.py lowercases and strips punctuation).
Audio and manifests go to --out-dir, which must be outside the git repository. Never commit them.

Example:
  python -I tools/accent_contest/prepare_public_set.py --out-dir ~/megathon-data/accent_contest/india_accent_cv
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import operator
import random
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


COMPARATORS = {">=": operator.ge, "<=": operator.le, "==": operator.eq, "!=": operator.ne}


def parse_where(spec: str):
    """Parse 'column>=2' style filters into (column, comparator, value)."""
    match = re.fullmatch(r"\s*(\w+)\s*(>=|<=|==|!=)\s*(.+?)\s*", spec)
    if not match:
        raise ValueError(f"bad --where {spec!r}; use COLUMN>=N, COLUMN<=N, COLUMN==V or COLUMN!=V")
    column, symbol, raw = match.groups()
    try:
        value = float(raw)
    except ValueError:
        value = raw
    return column, COMPARATORS[symbol], value


def row_passes(row: dict, filters) -> bool:
    for column, compare, value in filters:
        cell = row[column]
        if cell is None:
            return False
        if isinstance(value, float):
            cell = float(cell)
        if not compare(cell, value):
            return False
    return True


def decode_to_16k_mono(audio_bytes: bytes):
    """Decode any libsndfile-readable bytes (mp3, wav, flac...) to float32 mono 16 kHz."""
    import numpy as np
    import soundfile
    import soxr

    samples, rate = soundfile.read(io.BytesIO(audio_bytes), dtype="float32", always_2d=True)
    mono = samples.mean(axis=1)
    if rate != 16000:
        mono = soxr.resample(mono, rate, 16000)
    return np.clip(mono, -1.0, 1.0)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--repo", default="WillHeld/india_accent_cv", help="Hugging Face dataset id (must be public/ungated)")
    parser.add_argument("--revision", help="dataset commit sha; default is the current head, recorded in meta.json")
    parser.add_argument("--shard-glob", default="data/train-*.parquet", help="parquet shards inside the repo")
    parser.add_argument("--text-column", default="sentence")
    parser.add_argument("--audio-column", default="audio")
    parser.add_argument("--speaker-column", default="client_id")
    parser.add_argument("--id-column", default="path", help="source clip name, kept in the manifest as source_id")
    parser.add_argument("--where", action="append", default=[], help="row filter, repeatable, e.g. up_votes>=2 --where down_votes==0")
    parser.add_argument("--n", type=int, default=80, help="total clips")
    parser.add_argument("--per-speaker", type=int, default=5, help="clips per speaker, so n/per-speaker speakers")
    parser.add_argument("--min-seconds", type=float, default=3.0)
    parser.add_argument("--max-seconds", type=float, default=15.0)
    parser.add_argument("--min-words", type=int, default=4)
    parser.add_argument("--seed", type=int, default=20261011)
    parser.add_argument("--out-dir", type=Path, required=True, help="where wavs/ and clips.jsonl go (outside the repo)")
    args = parser.parse_args()

    if args.n < 1 or args.per_speaker < 1:
        parser.error("--n and --per-speaker must be positive")
    out_dir = args.out_dir.expanduser().resolve()
    repo_root = Path(__file__).resolve().parents[2]
    if repo_root == out_dir or repo_root in out_dir.parents:
        parser.error("--out-dir must be outside the git repository")
    filters = [parse_where(spec) for spec in args.where]

    import pyarrow.parquet as pq
    import soundfile
    from huggingface_hub import HfApi, HfFileSystem

    revision = args.revision or HfApi().dataset_info(args.repo).sha
    fs = HfFileSystem()
    prefix = f"datasets/{args.repo}@{revision}"
    shards = sorted(fs.glob(f"{prefix}/{args.shard_glob}"))
    if not shards:
        sys.exit(f"no parquet shards match {prefix}/{args.shard_glob}")

    def open_shard(path: str):
        # cache_type="none": exact byte ranges, no 5 MB read-ahead per tiny column chunk
        return pq.ParquetFile(fs.open(path, "rb", cache_type="none"), pre_buffer=True)

    def scan_shard(shard: str):
        """Return [(row_group, speakers)] using footer min/max stats, reading the column only if they are missing."""
        parquet = open_shard(shard)
        speaker_index = parquet.schema.names.index(args.speaker_column)
        found = []
        for group in range(parquet.metadata.num_row_groups):
            stats = parquet.metadata.row_group(group).column(speaker_index).statistics
            if stats is not None and stats.has_min_max and stats.min == stats.max:
                speakers = [stats.min]
            else:
                speakers = parquet.read_row_group(group, columns=[args.speaker_column]).column(0).unique().to_pylist()
            found.append((group, speakers))
        return shard, found

    # Stage 1: speaker -> [(shard, row_group)] from the parquet footers (no audio is read).
    speaker_groups: dict[str, list[tuple[str, int]]] = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        for shard, found in pool.map(scan_shard, shards):
            for group, speakers in found:
                for speaker in speakers:
                    speaker_groups.setdefault(speaker, []).append((shard, group))
    print(f"{len(shards)} shards, {len(speaker_groups)} speakers", file=sys.stderr)

    rng = random.Random(args.seed)
    speaker_order = sorted(speaker_groups)
    rng.shuffle(speaker_order)

    meta_columns = {args.text_column, args.speaker_column, args.id_column, *(column for column, _, _ in filters)}
    wav_dir = out_dir / "wavs"
    wav_dir.mkdir(parents=True, exist_ok=True)
    manifest_rows: list[dict] = []
    open_parquet: dict[str, object] = {}

    for speaker in speaker_order:
        if len(manifest_rows) >= args.n:
            break
        groups = list(speaker_groups[speaker])
        rng.shuffle(groups)
        taken = 0
        want = min(args.per_speaker, args.n - len(manifest_rows))
        speaker_tag = hashlib.sha256(speaker.encode("utf-8")).hexdigest()[:8]
        for shard, group in groups:
            if taken >= want:
                break
            parquet = open_parquet.setdefault(shard, open_shard(shard))
            table = parquet.read_row_group(group, columns=sorted(meta_columns | {args.audio_column}))
            rows = table.to_pylist()
            rng.shuffle(rows)
            for row in rows:
                if taken >= want:
                    break
                text = (row[args.text_column] or "").strip()
                if row[args.speaker_column] != speaker or len(text.split()) < args.min_words:
                    continue
                if not row_passes(row, filters) or not row[args.audio_column] or not row[args.audio_column].get("bytes"):
                    continue
                try:
                    samples = decode_to_16k_mono(row[args.audio_column]["bytes"])
                except Exception as error:  # undecodable clip: skip, do not abort the whole run
                    print(f"skip undecodable clip {row[args.id_column]}: {error}", file=sys.stderr)
                    continue
                seconds = len(samples) / 16000
                if not args.min_seconds <= seconds <= args.max_seconds:
                    continue
                clip_id = f"{speaker_tag}-{len(manifest_rows) + 1:03d}"
                soundfile.write(str(wav_dir / f"{clip_id}.wav"), samples, 16000, subtype="PCM_16")
                manifest_rows.append({
                    "clip_id": clip_id,
                    "wav": f"wavs/{clip_id}.wav",
                    "reference": text,
                    "speaker": speaker_tag,
                    "source_id": row[args.id_column],
                    "seconds": round(seconds, 2),
                })
                taken += 1
            del table, rows
        print(f"speaker {speaker_tag}: {taken} clips ({len(manifest_rows)}/{args.n})", file=sys.stderr)

    if not manifest_rows:
        sys.exit("no clips selected")
    (out_dir / "clips.jsonl").write_text(
        "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in manifest_rows), encoding="utf-8"
    )
    summary = {
        "repo": args.repo,
        "revision": revision,
        "seed": args.seed,
        "parameters": {key: (str(value) if isinstance(value, Path) else value) for key, value in vars(args).items()},
        "clips": len(manifest_rows),
        "speakers": len({row["speaker"] for row in manifest_rows}),
        "reference_words": sum(len(row["reference"].split()) for row in manifest_rows),
        "total_seconds": round(sum(row["seconds"] for row in manifest_rows), 1),
    }
    (out_dir / "meta.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    if len(manifest_rows) < args.n:
        print(f"warning: only {len(manifest_rows)} of {args.n} clips met the filters", file=sys.stderr)


if __name__ == "__main__":
    main()
