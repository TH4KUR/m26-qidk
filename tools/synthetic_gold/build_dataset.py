"""Build and audit authored synthetic examples. No model/API calls or real records."""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import random
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DEST = ROOT / "data/synthetic_gold/v1"
TYPES = (
    "water_leak_flood", "fire_smoke", "electrical", "spill_chemical", "injury",
    "illness", "near_miss", "property_damage", "equipment_failure", "security",
    "environmental", "other",
)
SEVERITIES = ("none", "minor", "moderate", "severe", "critical")
FIELDS = (
    "incident_type", "severity", "location", "date_time_text", "persons_involved",
    "equipment_asset", "immediate_actions", "witnesses",
)
SAVED_KEYS = {"date_time_text": "date_time"}
SAVED_ORDER = ("incident_type", "severity", "location", "date_time", "persons_involved",
               "equipment_asset", "description", "immediate_actions", "witnesses", "recommended_follow_up")
LIST_FIELDS = {"persons_involved", "witnesses"}


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def validate_rows(rows, require_complete_mandatory=False):
    """Mechanical checks only; evidence presence cannot prove semantic correctness."""
    ids, inputs, groups = set(), {}, {}
    for row in rows:
        label = row["id"]

        def require(condition, message):
            if not condition:
                raise ValueError(f"{label}: {message}")

        require(label not in ids, "duplicate id")
        ids.add(label)
        split, group = row["split"], row["group_id"]
        require(split in ("train", "validation", "test"), "invalid split")
        require(groups.get(group, split) == split, "group leakage across splits")
        groups[group] = split
        utterance, saved = row["raw_text"], row["report_before"]
        require(isinstance(utterance, str) and bool(utterance.strip()), "empty input")
        require(isinstance(saved, dict), "invalid saved report")
        require(set(saved) <= (set(FIELDS) - {"date_time_text"}) | {
            "date_time", "description", "recommended_follow_up"}, "unknown saved-report field")
        require(all(isinstance(v, str) and v for v in saved.values()), "saved values must be strings, as in PromptBuilder")
        key = compact([utterance.strip().casefold(), saved])
        require(key not in inputs, "duplicate input / exact-input leakage")
        inputs[key] = label
        target = row["target"]
        require(set(target) == {"tool", "arguments"} and target["tool"] == "update_report", "invalid tool envelope")
        args = target["arguments"]
        require(isinstance(args, dict) and set(args) <= set(FIELDS) | {"evidence"}, "unknown field")
        ev = args.get("evidence", {})
        require(isinstance(ev, dict), "evidence must be an object")
        emitted = set(args) - {"evidence"}
        require(set(ev) == emitted, "evidence keys must equal emitted fields")
        require(bool(emitted) or args == {}, "empty patch must have empty arguments")
        for field in emitted:
            value, quote = args[field], ev[field]
            require(isinstance(quote, str) and bool(quote.strip()) and quote in utterance, f"{field}: evidence must be an exact input span")
            if field in LIST_FIELDS:
                require(isinstance(value, list) and bool(value), f"{field}: nonempty list required")
                require(all(isinstance(v, str) and bool(v.strip()) and v in quote for v in value), f"{field}: ungrounded list item")
                require(len(value) == len(set(value)), f"{field}: repeated name")
                stored = ", ".join(value)
            else:
                require(isinstance(value, str) and bool(value.strip()), f"{field}: nonempty string required")
                stored = value
                if field == "incident_type":
                    require(value in TYPES, "unknown incident_type enum")
                elif field == "severity":
                    require(value in SEVERITIES, "unknown severity enum")
                else:
                    require(value in quote, f"{field}: value must preserve a literal input span")
            previous = saved.get(SAVED_KEYS.get(field, field))
            if field == "date_time_text" and previous:
                source = re.search(r"\((.*)\)$", previous)
                previous = source.group(1) if source else previous
            require(previous != stored, f"{field}: unchanged saved value must be omitted")
        require(not require_complete_mandatory or "mandatory_answers" in row, "mandatory answers annotation required")
        if "mandatory_answers" in row:
            answers = row["mandatory_answers"]
            require(set(answers) == {"incident_type", "severity", "location", "date_time_text"}, "all four mandatory answers required in every input")
            for field, answer in answers.items():
                value, quote = answer["value"], answer["evidence"]
                require(isinstance(value, str) and value and quote in utterance, f"{field}: missing mandatory evidence")
                if field == "incident_type":
                    require(value in TYPES, "invalid mandatory type")
                elif field == "severity":
                    require(value in SEVERITIES, "invalid mandatory severity")
                else:
                    require(value in quote, f"{field}: mandatory answer not grounded")
                if field in args:
                    require(args[field] == value, f"{field}: target disagrees with mandatory answer")
            if require_complete_mandatory:
                from temporal_gold import materialize, time_spec
                expected_spec = time_spec(answers["date_time_text"]["value"])
                require(row.get("temporal_gold") == expected_spec, "temporal oracle differs from authored phrase")
                resolved = materialize(expected_spec, datetime.fromisoformat("2026-10-11T11:00:00+05:30"))
                require(not resolved["needs_clarification"], "date and clock must both be answerable")
        if "messages" in row:
            messages = row["messages"]
            require([m["role"] for m in messages] == ["system", "user", "assistant"], "invalid messages")
            require(messages[1]["content"] == prompt_user(utterance, saved), "input differs from PromptBuilder format")
            require(messages[2]["content"] == compact(target), "assistant content differs from target")


def prompt_user(utterance, saved):
    ordered = {key: saved[key] for key in SAVED_ORDER if key in saved}
    return f'Report so far: {compact(ordered)}\nWorker: "{utterance.strip()}"'


def example(group, style, text, labels, tags=(), notes="", before=None):
    """Labels are authored (value, exact quote) pairs, never inferred by regex."""
    args = {field: value for field, (value, quote) in labels.items()}
    if labels:
        args["evidence"] = {field: quote for field, (value, quote) in labels.items()}
    return {
        "id": f"{group}-{style}", "group_id": group, "style": style,
        "raw_text": text, "report_before": before or {},
        "target": {"tool": "update_report", "arguments": args},
        "tags": list(tags), "annotation_notes": notes,
        "language": "en", "source": "authored_synthetic",
        "review_status": "machine_checked_not_independently_human_adjudicated",
    }


def assign_splits(rows):
    """75/12.5/12.5 by families, stratified by source category, fixed seed."""
    buckets = {}
    for row in rows:
        bucket = row["group_id"].rsplit("-", 1)[0]
        buckets.setdefault(bucket, set()).add(row["group_id"])
    assignment = {}
    rng = random.Random(20261011)
    for bucket, group_set in sorted(buckets.items()):
        groups = sorted(group_set)
        rng.shuffle(groups)
        held = max(1, len(groups) // 8)
        for i, group in enumerate(groups):
            assignment[group] = "test" if i < held else "validation" if i < 2 * held else "train"
    return [dict(row, split=assignment[row["group_id"]]) for row in rows]


def schema():
    nonempty = {"type": "string", "minLength": 1}
    properties = {f: dict(nonempty) for f in FIELDS}
    properties["incident_type"] = {"type": "string", "enum": list(TYPES)}
    properties["severity"] = {"type": "string", "enum": list(SEVERITIES)}
    for field in LIST_FIELDS:
        properties[field] = {"type": "array", "minItems": 1, "uniqueItems": True, "items": nonempty}
    properties["evidence"] = {
        "type": "object", "additionalProperties": False,
        "properties": {f: dict(nonempty) for f in FIELDS},
    }
    return {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "title": "Synthetic SFT sparse update_report target (stricter than legacy parser)",
        "type": "object", "additionalProperties": False,
        "required": ["tool", "arguments"], "properties": {
            "tool": {"const": "update_report"},
            "arguments": {"type": "object", "additionalProperties": False, "properties": properties,
                "dependentRequired": {f: ["evidence"] for f in FIELDS}},
        },
        "$comment": "Cross-field evidence equality and input grounding are checked by build_dataset.py.",
    }


def write_jsonl(path, rows):
    path.write_text("".join(compact(r) + "\n" for r in rows), encoding="utf-8")


def summarize(rows):
    counts = Counter(r["split"] for r in rows)
    tokenizer_check = "not_performed"
    check_file = DEST / "tokenizer_check.json"
    if check_file.exists():
        report = json.loads(check_file.read_text(encoding="utf-8"))
        digest = hashlib.sha256("".join(compact(r) + "\n" for r in rows).encode()).hexdigest()
        if report.get("gold_sha256") == digest and report.get("rows") == len(rows):
            tokenizer_check = "passed" if report.get("over_4096") == 0 else "failed"
    return {
        "rows": len(rows), "families": len({r["group_id"] for r in rows}),
        "split_rows": dict(sorted(counts.items())),
        "split_families": {s: len({r["group_id"] for r in rows if r["split"] == s}) for s in counts},
        "styles": dict(Counter(r["style"] for r in rows)),
        "incident_types": dict(Counter(r["target"]["arguments"].get("incident_type", "omitted") for r in rows)),
        "severities": dict(Counter(r["target"]["arguments"].get("severity", "omitted") for r in rows)),
        "field_counts": {f: sum(f in r["target"]["arguments"] for r in rows) for f in FIELDS},
        "no_op_rows": sum(r["target"]["arguments"] == {} for r in rows),
        "context_rows": sum(bool(r["report_before"]) for r in rows),
        "all_mandatory_answered_rows": sum(set(r.get("mandatory_answers", {})) == {"incident_type", "severity", "location", "date_time_text"} for r in rows),
        "date_and_clock_resolvable_rows": sum("date_and_clock_resolvable" in r["tags"] for r in rows),
        "tags": dict(Counter(tag for r in rows for tag in r["tags"])),
        "validation": {"schema_and_enum_checks": "passed", "exact_evidence_spans": "passed",
            "literal_value_grounding": "passed", "family_split_leakage": 0,
            "exact_input_duplicates": 0, "independent_human_review": "not_performed",
            "model_accuracy": "not_measured", "tokenizer_length_checks": tokenizer_check},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate committed outputs without rewriting them")
    args = parser.parse_args()
    if args.check:
        rows = [json.loads(line) for line in (DEST / "gold.jsonl").read_text(encoding="utf-8").splitlines()]
        validate_rows(rows, require_complete_mandatory=True)
        for split in ("train", "validation", "test"):
            expected = [{"messages": r["messages"]} for r in rows if r["split"] == split]
            actual = [json.loads(line) for line in (DEST / f"{split}.jsonl").read_text(encoding="utf-8").splitlines()]
            if actual != expected:
                raise ValueError(f"{split}: SFT export differs from gold")
        manifest = json.loads((DEST / "manifest.json").read_text(encoding="utf-8"))
        for name, digest in manifest["sha256"].items():
            if hashlib.sha256((DEST / name).read_bytes()).hexdigest() != digest:
                raise ValueError(f"{name}: manifest hash mismatch")
        for name, digest in manifest["source_sha256"].items():
            if hashlib.sha256((ROOT / name).read_bytes()).hexdigest() != digest:
                raise ValueError(f"{name}: source or app contract changed; regenerate and recheck")
        print(json.dumps(summarize(rows), indent=2))
        return
    from corpus import base_examples, edge_examples, context_examples, mandatory_complete, resolvable_times
    from temporal_gold import time_spec

    system = (DEST / "system_prompt.txt").read_text(encoding="utf-8").strip()
    rows = assign_splits(resolvable_times(mandatory_complete(base_examples() + edge_examples() + context_examples())))
    for row in rows:
        row["temporal_gold"] = time_spec(row["mandatory_answers"]["date_time_text"]["value"])
        row["messages"] = [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt_user(row["raw_text"], row["report_before"])},
            {"role": "assistant", "content": compact(row["target"])},
        ]
        row["omitted_fields"] = [f for f in FIELDS if f not in row["target"]["arguments"]]
    validate_rows(rows, require_complete_mandatory=True)
    write_jsonl(DEST / "gold.jsonl", rows)
    for split in ("train", "validation", "test"):
        write_jsonl(DEST / f"{split}.jsonl", [{"messages": r["messages"]} for r in rows if r["split"] == split])
    (DEST / "target.schema.json").write_text(json.dumps(schema(), indent=2) + "\n", encoding="utf-8")
    (DEST / "statistics.json").write_text(json.dumps(summarize(rows), indent=2) + "\n", encoding="utf-8")
    files = ["gold.jsonl", "train.jsonl", "validation.jsonl", "test.jsonl", "system_prompt.txt", "target.schema.json", "statistics.json"]
    files += [name for name in ("existing_e_benchmark.json", "tokenizer_check.json") if (DEST / name).exists()]
    revision = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()
    source_files = [Path(__file__), Path(__file__).with_name("corpus.py"), Path(__file__).with_name("temporal_gold.py")]
    contract_files = [ROOT / "core/src/main/kotlin/com/megathon/incident" / path for path in (
        "domain/Model.kt", "session/PromptBuilder.kt", "llm/ToolCallParser.kt", "llm/PayloadValidator.kt")]
    manifest = {
        "dataset": "incident-synthetic-gold-v1", "created_date": "2026-10-11", "seed": 20261011,
        "base_git_revision": revision, "independent_human_adjudication": False,
        "generation": "96 authored fact sets x 4 controlled variants, 24 edge families x 2, 24 context families x 2; no external records or model sampling",
        "sha256": {name: hashlib.sha256((DEST / name).read_bytes()).hexdigest() for name in files},
        "source_sha256": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files + contract_files},
    }
    (DEST / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summarize(rows), indent=2))


if __name__ == "__main__":
    main()
