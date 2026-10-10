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


def load_manifest(path: Path) -> list[Clip]:
    clips = []
    seen = set()
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not line.strip():
            continue
        try:
            row = json.loads(line)
            clip_id = row["clip_id"]
            reference = row["reference"]
            wav = (path.parent / row["wav"]).resolve()
        except (ValueError, KeyError, TypeError) as error:
            raise ValueError(f"invalid manifest line {line_number}: {error}") from error
        if not isinstance(clip_id, str) or not clip_id:
            raise ValueError(f"empty clip_id on line {line_number}")
        if clip_id in seen:
            raise ValueError(f"duplicate clip_id: {clip_id}")
        if not isinstance(reference, str) or not normalize(reference):
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


def render_table(rows: list[dict]) -> str:
    lines = [
        "| Rank | Model | WER | Errors / reference words | Clips |",
        "|---:|---|---:|---:|---:|",
    ]
    for rank, row in enumerate(sorted(rows, key=lambda value: (value["wer"], value["model"])), start=1):
        model = row["model"].replace("|", "\\|")
        lines.append(f"| {rank} | {model} | {row['wer']:.2%} | {row['errors']}/{row['reference_words']} | {row['clips']} |")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True, help="JSONL rows: clip_id, wav, reference")
    parser.add_argument("--output", type=Path, required=True, help="write Markdown table here")
    parser.add_argument("--predictions", type=Path, help="optional JSONL output with per-clip predictions")
    parser.add_argument("--model", action="append", dest="models", help="override the default four model IDs; repeatable")
    args = parser.parse_args()
    clips = load_manifest(args.manifest)
    models = args.models or list(DEFAULT_MODELS)

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
                model=model,
                device=0 if torch.cuda.is_available() else -1,
                chunk_length_s=30,
            )
        return pipelines[model](str(wav))["text"].strip()

    rows, predictions = run_contest(clips, models, transcribe)
    table = render_table(rows)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(table, encoding="utf-8")
    if args.predictions:
        args.predictions.parent.mkdir(parents=True, exist_ok=True)
        args.predictions.write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in predictions),
            encoding="utf-8",
        )
    print(table, end="")


if __name__ == "__main__":
    main()
