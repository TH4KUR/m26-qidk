"""Run the same English incident WAVs through four Whisper checkpoints."""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
import wave
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


DEFAULT_MODELS = (
    "openai/whisper-tiny.en",
    "openai/whisper-base.en",
    "theshivam7/whisper-tiny-aesrc-indian-english",
    "theshivam7/whisper-small-aesrc-indian-english",
)


@dataclass(frozen=True)
class Clip:
    clip_id: str
    wav: Path
    reference: str


def normalize(text: str) -> list[str]:
    text = unicodedata.normalize("NFKC", text).casefold()
    text = re.sub(r"[^\w\s]", " ", text, flags=re.UNICODE)
    return text.split()


def word_errors(reference: list[str], hypothesis: list[str]) -> int:
    previous = list(range(len(hypothesis) + 1))
    for index, expected in enumerate(reference, start=1):
        current = [index]
        for column, actual in enumerate(hypothesis, start=1):
            current.append(min(
                previous[column] + 1,
                current[column - 1] + 1,
                previous[column - 1] + (expected != actual),
            ))
        previous = current
    return previous[-1]


def parse_selection(values: list[str]) -> dict[str, str]:
    filters = {}
    for value in values:
        key, separator, expected = value.partition("=")
        key, expected = key.strip(), expected.strip()
        if not separator or not key or not expected or key in filters:
            raise ValueError(f"expected unique nonempty KEY=VALUE selection: {value!r}")
        filters[key] = expected
    return filters


def load_manifest(path: Path, *, filters: dict[str, str] | None = None, allow_empty_reference: bool = False) -> list[Clip]:
    clips = []
    seen = set()
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
            if not isinstance(row, dict):
                raise ValueError("expected a JSON object")
            if filters:
                missing = [key for key in filters if key not in row]
                if missing:
                    raise ValueError(f"missing selection metadata: {', '.join(missing)}")
                invalid = [key for key in filters if not isinstance(row[key], str) or not row[key].strip()]
                if invalid:
                    raise ValueError(f"invalid selection metadata: {', '.join(invalid)}")
                if any(row[key] != value for key, value in filters.items()):
                    continue
            clip_id = row["clip_id"]
            reference = row["reference"]
            wav = (path.parent / row["wav"]).resolve()
        except (ValueError, KeyError, TypeError) as error:
            raise ValueError(f"invalid manifest line {line_number}: {error}") from error
        if not isinstance(clip_id, str) or not clip_id:
            raise ValueError(f"empty clip_id on line {line_number}")
        if clip_id in seen:
            raise ValueError(f"duplicate clip_id: {clip_id}")
        if not isinstance(reference, str) or not (allow_empty_reference or normalize(reference)):
            raise ValueError(f"empty reference on line {line_number}: {clip_id}")
        if not wav.is_file():
            raise ValueError(f"missing WAV on line {line_number}: {wav}")
        with wave.open(str(wav), "rb") as audio:
            if (audio.getnchannels(), audio.getframerate(), audio.getsampwidth(), audio.getcomptype()) != (1, 16000, 2, "NONE"):
                raise ValueError(f"expected 16 kHz mono PCM16 WAV: {wav}")
        seen.add(clip_id)
        clips.append(Clip(clip_id, wav, reference))
    if not clips:
        raise ValueError("manifest has no clips")
    return clips


def aggregate_wer(clips: list[Clip], predictions: dict[str, str]) -> dict:
    errors = 0
    reference_words = 0
    for clip in clips:
        reference = normalize(clip.reference)
        hypothesis = normalize(predictions[clip.clip_id])
        errors += word_errors(reference, hypothesis)
        reference_words += len(reference)
    return {
        "errors": errors,
        "reference_words": reference_words,
        "clips": len(clips),
        "wer": errors / reference_words,
    }


def run_contest(
    clips: list[Clip], models: list[str], transcribe: Callable[[str, Path], str],
) -> tuple[list[dict], list[dict]]:
    rows = []
    all_predictions = []
    for model in models:
        predictions = {}
        for clip in clips:
            prediction = transcribe(model, clip.wav)
            predictions[clip.clip_id] = prediction
            all_predictions.append({"model": model, "clip_id": clip.clip_id, "reference": clip.reference, "prediction": prediction})
        rows.append({"model": model, **aggregate_wer(clips, predictions)})
    return sorted(rows, key=lambda row: (row["wer"], row["model"])), all_predictions


def raw_transcripts(predictions: list[dict]) -> list[dict]:
    """Export E's wire format without normalizing text or changing legacy rows."""
    return [{"clip_id": row["clip_id"], "model": row["model"], "text": row["prediction"]}
            for row in predictions]


def render_table(rows: list[dict]) -> str:
    lines = [
        "| Rank | Model | WER | Errors / reference words | Clips |",
        "|---:|---|---:|---:|---:|",
    ]
    for rank, row in enumerate(sorted(rows, key=lambda value: (value["wer"], value["model"])), start=1):
        model = row["model"].replace("|", "\\|")
        lines.append(f"| {rank} | {model} | {row['wer']:.2%} | {row['errors']}/{row['reference_words']} | {row['clips']} |")
    return "\n".join(lines) + "\n"


def resolve_model(model: str, models_dir: Path | None) -> str:
    if models_dir is None:
        return model
    local_path = models_dir / model.split("/")[-1]
    if not local_path.is_dir():
        raise ValueError(f"model directory missing: {local_path}")
    if not (local_path / "model.safetensors").is_file():
        raise ValueError(f"model weights missing: {local_path}")
    return str(local_path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True, help="JSONL rows: clip_id, wav, reference")
    parser.add_argument("--output", type=Path, help="write Markdown table here (required unless --transcripts-only)")
    parser.add_argument("--predictions", type=Path, help="optional JSONL output with per-clip predictions")
    parser.add_argument("--raw-transcripts", type=Path, help="optional JSONL output: clip_id, model, text")
    parser.add_argument("--transcripts-only", action="store_true",
                        help="only write --raw-transcripts: clips may have an empty reference, and no WER table or predictions file is written")
    parser.add_argument("--select", action="append", default=[], help="exact manifest metadata KEY=VALUE; repeatable")
    parser.add_argument("--model", action="append", dest="models", help="override the default four model IDs; repeatable")
    parser.add_argument("--models-dir", type=Path, help="directory containing locally downloaded model folders")
    parser.add_argument("--dataset-label", default="", help="heading for the output table, e.g. Synthetic smoke test")
    args = parser.parse_args()
    try:
        filters = parse_selection(args.select)
    except ValueError as error:
        parser.error(str(error))
    if args.transcripts_only:
        if not args.raw_transcripts:
            parser.error("--transcripts-only needs --raw-transcripts")
        if args.output or args.predictions:
            parser.error("--transcripts-only writes only --raw-transcripts (no --output or --predictions)")
    elif not args.output:
        parser.error("--output is required unless --transcripts-only")
    outputs = [path.resolve() for path in (args.output, args.predictions, args.raw_transcripts) if path]
    if len(set(outputs)) != len(outputs) or args.manifest.resolve() in outputs:
        parser.error("output paths must be distinct and must not overwrite the manifest")
    clips = load_manifest(args.manifest, filters=filters, allow_empty_reference=args.transcripts_only)
    models = args.models or list(DEFAULT_MODELS)
    for model in models:
        resolve_model(model, args.models_dir)

    import torch
    from transformers import pipeline

    pipelines = {}

    def transcribe(model: str, wav: Path) -> str:
        if model not in pipelines:
            pipelines.clear()  # avoid keeping several Whisper models in GPU memory
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
            pipelines[model] = pipeline(
                "automatic-speech-recognition",
                model=resolve_model(model, args.models_dir),
                device=0 if torch.cuda.is_available() else -1,
                chunk_length_s=30,
            )
        return pipelines[model](str(wav))["text"].strip()

    if args.transcripts_only:
        # No reference, no WER: just what each model heard, for every clip, in E's wire format.
        heard = [{"model": model, "clip_id": clip.clip_id, "reference": clip.reference, "prediction": transcribe(model, clip.wav)}
                 for model in models for clip in clips]
        args.raw_transcripts.parent.mkdir(parents=True, exist_ok=True)
        args.raw_transcripts.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in raw_transcripts(heard)), encoding="utf-8")
        print(f"wrote {len(heard)} raw transcripts for {len(clips)} clips and {len(models)} model(s) to {args.raw_transcripts}")
        return
    rows, predictions = run_contest(clips, models, transcribe)
    table = render_table(rows)
    if args.dataset_label:
        table = f"# {args.dataset_label}\n\n{table}"
    if filters:
        table = "Selection: " + ", ".join(f"{key}={value}" for key, value in filters.items()) + "\n\n" + table
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(table, encoding="utf-8")
    if args.predictions:
        args.predictions.parent.mkdir(parents=True, exist_ok=True)
        args.predictions.write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in predictions),
            encoding="utf-8",
        )
    if args.raw_transcripts:
        args.raw_transcripts.parent.mkdir(parents=True, exist_ok=True)
        args.raw_transcripts.write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in raw_transcripts(predictions)),
            encoding="utf-8",
        )
    print(table, end="")


if __name__ == "__main__":
    main()
