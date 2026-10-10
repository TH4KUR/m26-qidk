"""Python port of C's ToolCallParser (core/.../llm/ToolCallParser.kt).

Lenient about syntax noise (think blocks, fences, <tool_call> tags, prose, trailing commas),
strict about content: exactly one complete update_report envelope with typed arguments, no
salvage of truncated output, no bare arguments object, no duplicate keys.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field

TOOL_NAME = "update_report"
STRING_FIELDS = ("incident_type", "severity", "location", "date_time_text", "equipment_asset", "immediate_actions")
LIST_FIELDS = ("persons_involved", "witnesses")

THINK = re.compile(r"<think>.*?(</think>|$)", re.DOTALL)
FENCE = re.compile(r"```[a-zA-Z]*")
TOOL_CALL_TAGS = re.compile(r"</?tool_call>")


@dataclass
class ParseResult:
    ok: bool
    code: str | None = None          # failure code, same names as C's FailureCode
    payload: dict = field(default_factory=dict)
    envelope: str | None = None      # "JSON" or "NATIVE"
    raw: str = ""


class _Duplicate(Exception):
    pass


def _no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise _Duplicate
    return dict(pairs)


def strip_noise(raw: str) -> str:
    return TOOL_CALL_TAGS.sub(" ", FENCE.sub(" ", THINK.sub(" ", raw)))


def remove_trailing_commas(text: str) -> str:
    out, in_string, escaped, i = [], False, False, 0
    while i < len(text):
        c = text[i]
        if in_string:
            out.append(c)
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
            out.append(c)
        elif c == ",":
            j = i + 1
            while j < len(text) and text[j].isspace():
                j += 1
            if not (j < len(text) and text[j] in "}]"):
                out.append(c)
        else:
            out.append(c)
        i += 1
    return "".join(out)


def scan_objects(text: str) -> tuple[list[str], bool]:
    """Top-level {...} spans respecting strings; second value is True when the tail is unterminated."""
    found, depth, start, in_string, escaped = [], 0, -1, False, False
    for i, c in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
            continue
        if c == '"':
            if depth > 0:
                in_string = True
        elif c == "{":
            if depth == 0:
                start = i
            depth += 1
        elif c == "}" and depth > 0:
            depth -= 1
            if depth == 0:
                found.append(text[start:i + 1])
    return found, depth > 0


def _is_null_literal(v) -> bool:
    return isinstance(v, str) and (v.strip() == "" or v.strip().lower() == "null")


def _nullify(v):
    if isinstance(v, dict):
        return {k: _nullify(x) for k, x in v.items()}
    if isinstance(v, list):
        return [_nullify(x) for x in v if not _is_null_literal(x)]
    return None if _is_null_literal(v) else v


def _decode(args: dict) -> dict | None:
    """Typed decode of ExtractionPayload; None when a known field has the wrong type."""
    args = _nullify(args)
    out: dict = {}
    for k in STRING_FIELDS:
        v = args.get(k)
        if v is not None and not isinstance(v, str):
            return None
        out[k] = v
    for k in LIST_FIELDS:
        v = args.get(k, [])
        if v is None:
            v = []
        if not isinstance(v, list) or not all(isinstance(x, str) for x in v):
            return None
        out[k] = v
    ev = args.get("evidence", {}) or {}
    if not isinstance(ev, dict) or not all(isinstance(x, str) for x in ev.values()):
        return None  # Map<String, String> in Kotlin: null (incl. "" nullified) or a list fails the decode
    out["evidence"] = dict(ev)
    # Optional urgency hints: unparseable values are dropped, numeric strings accepted.
    hint = args.get("impact_hint")
    if hint is not None:
        try:
            out["impact_hint"] = int(str(hint).strip())
        except ValueError:
            pass
    ongoing = args.get("ongoing")
    if ongoing is not None and str(ongoing).strip().lower() in ("true", "false"):
        out["ongoing"] = str(ongoing).strip().lower() == "true"
    return out


def parse(raw: str) -> ParseResult:
    objects, truncated = scan_objects(strip_noise(raw))
    envelopes = []
    for text in objects:
        try:
            obj = json.loads(remove_trailing_commas(text), object_pairs_hook=_no_duplicates)
        except _Duplicate:
            return ParseResult(False, "DUPLICATE_KEY", raw=raw)
        except json.JSONDecodeError:
            continue
        if not isinstance(obj, dict):
            continue
        if "tool" in obj:
            envelopes.append((obj.get("tool"), obj.get("arguments"), "JSON"))
        elif "name" in obj and "arguments" in obj:
            envelopes.append((obj.get("name"), obj.get("arguments"), "NATIVE"))

    if len(envelopes) > 1:
        return ParseResult(False, "MULTIPLE_ENVELOPES", raw=raw)
    if not envelopes:
        return ParseResult(False, "TRUNCATED" if truncated else "NO_ENVELOPE", raw=raw)
    name, args, kind = envelopes[0]
    if name != TOOL_NAME:
        return ParseResult(False, "UNKNOWN_TOOL", raw=raw)
    if not isinstance(args, dict):
        return ParseResult(False, "INVALID_ARGUMENTS", raw=raw)
    payload = _decode(args)
    if payload is None:
        return ParseResult(False, "INVALID_ARGUMENTS", raw=raw)
    return ParseResult(True, None, payload, kind, raw)
