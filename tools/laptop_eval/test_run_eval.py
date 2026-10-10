"""run_eval drives an LLM over the gold set. The model call is injected, so these tests use a
scripted fake instead of a llama-server."""

import json

from run_eval import apply_raw, build_messages, run_items

DECK = "There was a water leak near Conference Room 2 around 10 AM. Maintenance has already isolated the area."
DECK_OUT = ('{"tool":"update_report","arguments":{"incident_type":"water_leak_flood","location":"Conference Room 2",'
            '"date_time_text":"around 10 AM","immediate_actions":"isolated the area",'
            '"evidence":{"location":"near Conference Room 2"}}}')


class Scripted:
    def __init__(self, *outs):
        self.outs, self.calls = list(outs), []

    def __call__(self, messages):
        self.calls.append(messages)
        return {"text": self.outs.pop(0), "prompt_tokens": 100, "completion_tokens": 40, "ms": 5.0}


def test_raw_mapping_writes_reported_fields_with_upper_case_keys():
    fields = apply_raw({}, {"incident_type": "water_leak_flood", "location": "Conference Room 2",
                            "date_time_text": "around 10 AM", "witnesses": ["Ravi", "Priya"], "evidence": {}})
    assert fields["LOCATION"] == {"text": "Conference Room 2", "status": "REPORTED"}
    assert fields["DATE_TIME"] == {"text": "around 10 AM", "status": "REPORTED", "raw": True}
    assert fields["WITNESSES"]["text"] == "Ravi, Priya"


def test_raw_mapping_later_value_overwrites_and_null_keeps():
    f1 = apply_raw({}, {"location": "Conference Room 2"})
    f2 = apply_raw(f1, {"location": "Conference Room 3", "incident_type": None})
    assert f2["LOCATION"]["text"] == "Conference Room 3"
    assert "INCIDENT_TYPE" not in f2


def test_messages_carry_system_prompt_report_so_far_and_utterance():
    msgs = build_messages("SYS", {"LOCATION": {"text": "Lab 3", "status": "REPORTED"}}, "it is minor", no_think=True)
    assert msgs[0] == {"role": "system", "content": "SYS"}
    assert '"location": "Lab 3"' in msgs[1]["content"]
    assert msgs[1]["content"].rstrip().endswith("/no_think")
    assert 'Worker: "it is minor"' in msgs[1]["content"]


def test_multi_turn_item_calls_once_per_turn_and_merges():
    item = {"id": "g34", "turns": [DECK, "Actually it was Conference Room 3."]}
    llm = Scripted(DECK_OUT, '{"tool":"update_report","arguments":{"location":"Conference Room 3"}}')
    raw, preds = run_items([item], llm, "SYS")
    assert len(llm.calls) == 2
    assert preds[0]["report"]["fields"]["LOCATION"]["text"] == "Conference Room 3"
    assert preds[0]["report"]["fields"]["INCIDENT_TYPE"]["text"] == "water_leak_flood"
    assert [r["turn"] for r in raw] == [0, 1]


def test_parse_failure_is_logged_and_leaves_report_unchanged():
    item = {"id": "g01", "turns": [DECK]}
    raw, preds = run_items([item], Scripted("Sure! Here is the report."), "SYS")
    assert raw[0]["parse_code"] == "NO_ENVELOPE"
    assert preds[0]["report"]["fields"] == {}


def test_raw_log_keeps_output_and_token_counts():
    raw, _ = run_items([{"id": "g01", "turns": [DECK]}], Scripted(DECK_OUT), "SYS")
    assert raw[0]["output"] == DECK_OUT
    assert (raw[0]["prompt_tokens"], raw[0]["completion_tokens"]) == (100, 40)
    json.dumps(raw)  # serialisable


def test_misheard_variant_feeds_the_misheard_turns():
    item = {"id": "h04", "turns": ["clean words"], "turns_misheard": ["water leek near seminar hall to"]}
    llm = Scripted(DECK_OUT)
    run_items([item], llm, "SYS", turns_key="turns_misheard")
    assert "water leek near seminar hall to" in llm.calls[0][1]["content"]
