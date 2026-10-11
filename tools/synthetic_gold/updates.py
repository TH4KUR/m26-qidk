"""Authored four-turn sequences: report, correct, correct again, confirm.

Every call is an app-format snapshot plus current worker words. Narrative states
are authored first; sparse targets contain only facts that changed. The complete
state oracle stays in metadata so sequence integrity can be checked without
teaching the model to copy an old report or output a whole report.
"""
from __future__ import annotations

import copy
from datetime import datetime

from build_dataset import FIELDS, LIST_FIELDS, SAVED_KEYS, example
from corpus import MANDATORY, TYPE_DESCRIPTIONS
from temporal_gold import materialize, time_spec

CAPTURE = "2026-10-11T11:00:00+05:30"
TYPE_CHAINS = [
    ("equipment_failure", "electrical", "fire_smoke"),
    ("injury", "near_miss", "property_damage"),
    ("water_leak_flood", "spill_chemical", "environmental"),
    ("other", "security", "property_damage"),
    ("electrical", "equipment_failure", "fire_smoke"),
    ("property_damage", "near_miss", "injury"),
    ("illness", "injury", "near_miss"),
    ("environmental", "spill_chemical", "water_leak_flood"),
]
SEVERITY_CHAINS = [
    ("minor", "severe", "moderate"), ("critical", "moderate", "minor"),
    ("none", "minor", "moderate"), ("severe", "critical", "severe"),
    ("moderate", "none", "minor"), ("minor", "moderate", "severe"),
    ("critical", "severe", "none"), ("severe", "minor", "critical"),
]
TIME_CHAINS = [
    ("about five minutes ago", "about fifty minutes ago", "about fifteen minutes ago"),
    ("yesterday at 4 PM", "yesterday at 7 PM", "the day before yesterday at 6:30 PM"),
    ("last Friday at 3 PM", "last Thursday at 4 PM", "last Sunday at 3 PM"),
    ("around 10 AM today", "yesterday around 10 AM", "at a quarter to nine this morning"),
    ("about twenty minutes ago", "two hours ago", "about an hour ago"),
    ("today around 7 AM", "yesterday at 6 PM", "the day before yesterday at 6:30 PM"),
    ("about five minutes before 9 AM today", "two hours after the 9 AM class today", "at ten yesterday morning"),
    ("today at about 12:05 AM", "around eleven last night", "yesterday at 6 PM"),
]
ACTION_CHAINS = [
    ("called security", "informed the supervisor", "called maintenance"),
    ("isolated the area", "isolated the area and called security", "called security"),
    ("switched off the supply", "closed the valve", "disconnected the machine"),
    ("arranged first aid", "called the medical desk", "called the medical desk and arranged transport"),
    ("put up a warning sign", "blocked the doorway", "blocked the doorway and put up a warning sign"),
    ("informed maintenance", "cordoned off the area", "cordoned off the area and informed maintenance"),
    ("moved people away", "closed the gate", "moved people away and closed the gate"),
    ("tagged the unit out of service", "called the supervisor", "tagged the unit out of service and called the supervisor"),
]
PEOPLE = [
    ("Nila", "Neela", "Leela", "Mira"), ("Aman", "Dev", "Rohan", "Imran"),
    ("Anika", "Diya", "Esha", "Fiza"), ("Gauri", "Hema", "Ira", "Jaya"),
    ("Kiran", "Lalit", "Manav", "Nitin"), ("Ojas", "Pranav", "Rajat", "Sahil"),
    ("Tara", "Uma", "Vani", "Zoya"), ("Kabir", "Neeraj", "Omar", "Vinay"),
]
WITNESSES = [
    ("Saira", "Sara", "Sana", "Rina"), ("Harsh", "Yash", "Ved", "Adil"),
    ("Juhi", "Alia", "Bhavya", "Chitra"), ("Deepa", "Farah", "Geeta", "Heena"),
    ("Jay", "Kunal", "Mohan", "Naveen"), ("Parth", "Ravi", "Sameer", "Tarun"),
    ("Veena", "Wafa", "Yamini", "Zeenat"), ("Akash", "Bharat", "Chetan", "Dinesh"),
]
SUBJECTS = ("pipe", "motor", "display unit", "cabinet", "access gate", "panel", "pump", "trolley")
LOCATION_CHAINS = [
    ("near Lab A2", "inside Lab A2", "outside Lab A2"),
    ("in Room 2", "in Room 20", "in Room 12"),
    ("at the north entrance", "at the south entrance", "near the south entrance"),
    ("in the east store", "in the west store", "outside the west store"),
    ("on the first floor", "on the second floor", "on the ground floor"),
    ("beside the Library", "inside the Library", "opposite the Library"),
    ("in Conference Room 1", "in Conference Room 3", "in Conference Room 2"),
    ("at the loading bay", "behind the loading bay", "at the service entrance"),
]
PREFIXES = (
    "Correction to what I told you.", "Actually, I got that detail wrong.",
    "Sorry, please change my earlier answer.", "I checked again; use this version.",
    "Wait, I mixed up one detail.", "Please update the report with this correction.",
    "No, let me correct that part.", "Er, correction, this is the accurate version.",
)


def list_chain(names, variant):
    """Full replacement lists: spelling, addition, removal, replacement."""
    a, b, c, d = names
    return [
        ([a], [b], [c]), ([a], [a, b], [b]),
        ([a, b], [c], [c, d]), ([a, b, c], [a, c], [d]),
        ([a], [a, b], [a, c]), ([a, b], [b], [b, c]),
        ([a, b], [c, d], [c]), ([a], [b, c], [d]),
    ][variant]


def authored_states(number, variant):
    """Three complete narrative states, before choosing which fields change."""
    affected = list_chain(PEOPLE[variant], variant)
    witnesses = list_chain(WITNESSES[variant], variant)
    states = []
    for step in range(3):
        states.append({
            "incident_type": TYPE_CHAINS[variant][step],
            "severity": SEVERITY_CHAINS[variant][step],
            "location": LOCATION_CHAINS[variant][step],
            "date_time_text": TIME_CHAINS[variant][step],
            "persons_involved": affected[step],
            "equipment_asset": f"{SUBJECTS[(variant + step) % len(SUBJECTS)]} U{number:03d}-{step + 1}",
            "immediate_actions": ACTION_CHAINS[variant][step],
            "witnesses": witnesses[step],
        })
    return states


def sentence(field, value):
    """Literal value and role/time evidence; no hidden facts in labels."""
    if field == "incident_type":
        return f"For this incident, {TYPE_DESCRIPTIONS[value]}"
    if field == "severity":
        return f"I rate the severity {value}"
    if field == "location":
        return f"The incident was {value}"
    if field == "date_time_text":
        return f"The incident happened {value}"
    if field == "persons_involved":
        names = " and ".join(value)
        return f"{names} {'was' if len(value) == 1 else 'were'} directly affected"
    if field == "witnesses":
        return f"{' and '.join(value)} witnessed the incident"
    if field == "equipment_asset":
        return f"The affected asset was {value}"
    return f"We {value}"


def saved_state(facts):
    saved = {}
    for field, value in facts.items():
        if field in LIST_FIELDS:
            value = ", ".join(value)
        elif field == "date_time_text":
            temporal = materialize(time_spec(value), datetime.fromisoformat(CAPTURE))
            clock = ("≈" if temporal["approximate"] else "") + temporal["time"]
            value = f"{temporal['date']} {clock} ({value})"
        saved[SAVED_KEYS.get(field, field)] = value
    return saved


def operation_for(before, after, changed):
    if not before:
        return "initial"
    if not changed:
        return "confirm"
    if len(changed) > 1:
        return "correct_multiple"
    field = next(iter(changed))
    if field in LIST_FIELDS:
        old, new = set(before[field]), set(after[field])
        if old < new:
            return "add_names"
        if new < old:
            return "remove_names"
        return "replace_names"
    if field == "immediate_actions" and before[field] in after[field]:
        return "add_completed_action"
    if field == "immediate_actions" and after[field] in before[field]:
        return "remove_completed_action"
    return "replace"


def sequence(group, states, focus, variant, role_swap=False):
    """Each next input is seeded by the previous authored full report state."""
    rows = []
    previous = None
    for turn, facts in enumerate(states + [copy.deepcopy(states[-1])]):
        old = states[turn - 1] if turn else {}
        changed = {f for f in FIELDS if old.get(f) != facts[f]}
        spoken = set(FIELDS) if turn in (0, 3) else set(MANDATORY) | changed
        # One repeated optional detail tests preservation without an extra patch.
        if turn in (1, 2):
            unchanged_optional = [f for f in FIELDS if f not in changed and f not in MANDATORY]
            if unchanged_optional:
                spoken.add(unchanged_optional[variant % len(unchanged_optional)])
        order = list(FIELDS)
        if variant % 2:
            order = order[4:] + order[:4]
        clauses = {f: sentence(f, facts[f]) for f in spoken}
        intro = "Please record this incident." if turn == 0 else PREFIXES[(variant + turn) % len(PREFIXES)]
        if turn == 3:
            intro = ("Yes, that is the final version.", "Reading it back; those details are correct.",
                     "No further changes; this is what happened.", "I confirm the latest report.")[variant % 4]
        text = intro + " " + ". ".join(clauses[f] for f in order if f in clauses) + "."
        operation = operation_for(old, facts, changed)
        if turn in (1, 2) and len(changed) == 1:
            field = next(iter(changed))
            if field in LIST_FIELDS:
                removed = [name for name in old[field] if name not in facts[field]]
                if removed:
                    role = "directly affected" if field == "persons_involved" else "a witness"
                    text += " " + ". ".join(f"{name} was not {role}" for name in removed) + "."
            elif field in ("severity", "location", "date_time_text", "equipment_asset"):
                text += f" My earlier answer, {old[field]}, was wrong."
            elif field == "immediate_actions":
                text += " The action detail I gave earlier was inaccurate."
        if turn == 3 and variant % 2:
            text += " We will ask Tara to inspect it tomorrow."
        labels = {f: (facts[f], clauses[f]) for f in FIELDS if f in changed}
        tags = ["update_sequence", "all_mandatory_answered", "date_and_clock_resolvable", focus]
        if turn:
            tags += ["saved_context", "preserve_unchanged_fields"]
        if turn in (1, 2):
            tags += ["previously_reported_value", "repeated_correction", operation]
            tags += ["mandatory_update"] if changed & set(MANDATORY) else []
            tags += ["optional_update"] if changed - set(MANDATORY) else []
        if turn == 3:
            tags += ["no_op", "confirmation_after_corrections"]
            if variant % 2:
                tags.append("planned_action_distractor")
        if role_swap:
            tags.append("role_swap")
        style = ("sequence_initial", "sequence_update_1", "sequence_update_2", "sequence_confirm")[turn]
        row = example(group, style, text, labels, tags,
                      "Authored four-turn sequence; current words support all mandatory answers. "
                      "Only changed facts belong in the patch. Lists replace the full saved list. "
                      "The complete after-state is annotation metadata, never a model message.",
                      saved_state(old) if old else {})
        row["mandatory_answers"] = {f: {"value": facts[f], "evidence": clauses[f]} for f in MANDATORY}
        row["transition"] = {
            "turn_index": turn, "previous_id": previous,
            "operation": operation, "changed_fields": [f for f in FIELDS if f in changed],
            "captured_at": CAPTURE, "expected_report_after": saved_state(facts),
        }
        previous = row["id"]
        rows.append(row)
    return rows


def update_examples():
    rows = []
    for field_index, field in enumerate(FIELDS):
        for variant in range(8):
            number = field_index * 8 + variant + 1
            alternatives = authored_states(number, variant)
            states = [alternatives[0]]
            for step in (1, 2):
                state = copy.deepcopy(states[-1])
                state[field] = alternatives[step][field]
                states.append(state)
            rows.extend(sequence(f"update-{field}-{variant + 1:02d}", states, field, variant))

    masks = [
        (("incident_type", "severity", "location"), ("equipment_asset", "date_time_text")),
        (("persons_involved", "witnesses"), ("persons_involved", "witnesses")),
        (("witnesses", "immediate_actions"), ("witnesses", "date_time_text")),
        (("location", "equipment_asset"), ("location", "severity")),
        (("date_time_text", "severity"), ("date_time_text", "immediate_actions")),
        (("persons_involved", "equipment_asset"), ("persons_involved", "location")),
        (tuple(MANDATORY), tuple(f for f in FIELDS if f not in MANDATORY)),
        (tuple(f for f in FIELDS if f not in MANDATORY), tuple(MANDATORY)),
    ]
    for index in range(16):
        variant = index % 8
        alternatives = authored_states(65 + index, variant)
        first, second = masks[variant]
        states = [alternatives[0]]
        for step, fields in enumerate((first, second), 1):
            state = copy.deepcopy(states[-1])
            for field in fields:
                state[field] = alternatives[step][field]
                if state[field] == states[-1][field]:
                    state[field] = alternatives[1][field]
            states.append(state)
        role_swap = variant == 1
        if role_swap:
            states[0]["persons_involved"], states[0]["witnesses"] = ["Aman"], ["Harsh"]
            states[1]["persons_involved"], states[1]["witnesses"] = ["Harsh"], ["Aman"]
            states[2]["persons_involved"], states[2]["witnesses"] = ["Dev"], ["Yash"]
        rows.extend(sequence(f"update-mixed-{index + 1:02d}", states, "multiple_field_update", variant, role_swap))
    return rows
