"""The Python parser must accept and reject exactly what C's Kotlin ToolCallParser does, so the
laptop/GPU contest scores the same outputs the app would keep."""

import subprocess
from pathlib import Path

import pytest

from parse import parse

REPO = Path(__file__).resolve().parents[2]
FIXTURES = "core/src/test/resources/fixtures/llm"


def fixture(name: str) -> str:
    local = REPO / FIXTURES / name
    if local.exists():
        return local.read_text(encoding="utf-8")
    # Until C's branch is merged into ours, read the fixture straight from git (read-only).
    try:
        return subprocess.run(["git", "show", f"origin/c/core-rules:{FIXTURES}/{name}"], cwd=REPO,
                              capture_output=True, text=True, check=True).stdout
    except subprocess.CalledProcessError:
        pytest.skip(f"fixture {name} not available")


# fixture -> expected failure code, or None for a successful parse (C's ToolCallParserTest)
FIXTURE_EXPECT = {
    "valid.txt": None, "fenced.txt": None, "native_envelope.txt": None, "no_op.txt": None,
    "extra_properties.txt": None, "with_hint.txt": None, "garbage_hint.txt": None,
    "prose_only.txt": "NO_ENVELOPE", "truncated.txt": "TRUNCATED", "two_envelopes.txt": "MULTIPLE_ENVELOPES",
    "wrong_tool.txt": "UNKNOWN_TOOL", "duplicate_scalar_writes.txt": "DUPLICATE_KEY",
}


@pytest.mark.parametrize("name,code", FIXTURE_EXPECT.items())
def test_c_fixture(name, code):
    r = parse(fixture(name))
    assert (r.ok, r.code) == (code is None, code)


def test_json_envelope_fields_and_evidence():
    r = parse(fixture("valid.txt"))
    assert r.payload["location"] == "Conference Room 2"
    assert r.payload["evidence"]["location"] == "near Conference Room 2"
    assert r.envelope == "JSON"


def test_native_envelope():
    r = parse(fixture("native_envelope.txt"))
    assert (r.envelope, r.payload["incident_type"]) == ("NATIVE", "fire_smoke")


def test_think_block_is_stripped():
    assert parse('<think>{"tool":"x"}</think>{"tool":"update_report","arguments":{"location":"Lab 3"}}').payload["location"] == "Lab 3"


def test_trailing_commas_tolerated():
    assert parse('{"tool":"update_report","arguments":{"witnesses":["Ravi",],}}').payload["witnesses"] == ["Ravi"]


def test_brace_inside_string():
    assert parse('{"tool":"update_report","arguments":{"location":"Room {B}"}}').payload["location"] == "Room {B}"


def test_literal_null_and_empty_strings_become_null_and_drop_from_lists():
    p = parse('{"tool":"update_report","arguments":{"location":"null","equipment_asset":"","witnesses":["", "Ravi"]}}').payload
    assert (p["location"], p["equipment_asset"], p["witnesses"]) == (None, None, ["Ravi"])


def test_hindi_text_kept():
    assert parse('{"tool":"update_report","arguments":{"location":"रसोई"}}').payload["location"] == "रसोई"


def test_bare_arguments_object_is_rejected():
    assert parse('{"location":"Lobby"}').code == "NO_ENVELOPE"


def test_wrong_value_type_fails():
    assert parse('{"tool":"update_report","arguments":{"witnesses":"Ravi"}}').code == "INVALID_ARGUMENTS"


def test_number_for_string_field_fails():
    assert parse('{"tool":"update_report","arguments":{"location":3}}').code == "INVALID_ARGUMENTS"


def test_unknown_keys_are_dropped():
    assert "mood" not in parse(fixture("extra_properties.txt")).payload


def test_hints_numeric_string_accepted_and_garbage_dropped():
    assert parse('{"tool":"update_report","arguments":{"impact_hint":"4"}}').payload["impact_hint"] == 4
    p = parse(fixture("garbage_hint.txt")).payload
    assert (p["location"], p.get("impact_hint"), p.get("ongoing")) == ("Lab", None, None)


def test_duplicate_key_inside_evidence_rejected():
    raw = '{"tool":"update_report","arguments":{"evidence":{"location":"a","location":"b"}}}'
    assert parse(raw).code == "DUPLICATE_KEY"


def test_duplicate_key_in_different_objects_is_fine():
    raw = '{"tool":"update_report","arguments":{"location":"a","evidence":{"location":"in a"}}}'
    assert parse(raw).ok


def test_null_or_list_inside_evidence_fails_like_kotlin_map_of_strings():
    # kotlinx decodes evidence as Map<String, String>: a null or list value is a decode error.
    assert parse('{"tool":"update_report","arguments":{"evidence":{"equipment_asset":null}}}').code == "INVALID_ARGUMENTS"
    assert parse('{"tool":"update_report","arguments":{"evidence":{"witnesses":[]}}}').code == "INVALID_ARGUMENTS"
    # C turns "" into null before decoding, so an empty evidence string fails too.
    assert parse('{"tool":"update_report","arguments":{"evidence":{"location":""}}}').code == "INVALID_ARGUMENTS"
