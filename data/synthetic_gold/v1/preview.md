# Dataset preview
Four training examples from the 480-row dataset. Every raw input supports all four mandatory answers. Relative-time gold below uses a fixed illustration reference of **11 October 2026, 11:00 AM IST**; the evaluator materializes it from the actual recorded reference at run time.

## Direct report

`base-water_leak_flood-03-direct` · training split

Input:

> A burst pipe flooded the passage near North 204. It happened yesterday at about 7 PM. I rate the severity as critical. We cordoned off the passage. Bina saw the incident.

Expected model output:

```json
{
  "tool": "update_report",
  "arguments": {
    "incident_type": "water_leak_flood",
    "severity": "critical",
    "location": "near North 204",
    "date_time_text": "yesterday at about 7 PM",
    "immediate_actions": "cordoned off the passage",
    "witnesses": [
      "Bina"
    ],
    "equipment_asset": "pipe",
    "evidence": {
      "incident_type": "A burst pipe flooded the passage",
      "severity": "I rate the severity as critical",
      "location": "near North 204",
      "date_time_text": "yesterday at about 7 PM",
      "immediate_actions": "We cordoned off the passage",
      "witnesses": "Bina saw the incident",
      "equipment_asset": "A burst pipe flooded the passage"
    }
  }
}
```

Resolved time for the illustration reference: **2026-10-10 19:00 IST** (approximate).

## Vague wording with complete mandatory answers

`base-water_leak_flood-03-vague` · training split

Input:

> What I can tell you is: A burst pipe flooded the passage near North 204. It was about five minutes before 9 AM today. I rate this as life-threatening. I do not have further details.

Expected model output:

```json
{
  "tool": "update_report",
  "arguments": {
    "incident_type": "water_leak_flood",
    "location": "near North 204",
    "equipment_asset": "pipe",
    "date_time_text": "about five minutes before 9 AM today",
    "severity": "critical",
    "evidence": {
      "incident_type": "A burst pipe flooded the passage",
      "location": "near North 204",
      "equipment_asset": "A burst pipe flooded the passage",
      "date_time_text": "about five minutes before 9 AM today",
      "severity": "I rate this as life-threatening"
    }
  }
}
```

Resolved time for the illustration reference: **2026-10-11 08:55 IST** (approximate).

## Time and optional-field distinctions

`edge-01-contrast_a` · training split

Input:

> The printer stopped working in North 101 about fifteen minutes ago. I reported it just now. We tagged it out of service. My rating for the incident is critical.

Expected model output:

```json
{
  "tool": "update_report",
  "arguments": {
    "incident_type": "equipment_failure",
    "location": "in North 101",
    "equipment_asset": "printer",
    "date_time_text": "about fifteen minutes ago",
    "immediate_actions": "tagged it out of service",
    "evidence": {
      "incident_type": "printer stopped working",
      "location": "in North 101",
      "equipment_asset": "printer stopped working",
      "date_time_text": "about fifteen minutes ago",
      "immediate_actions": "We tagged it out of service",
      "severity": "My rating for the incident is critical"
    },
    "severity": "critical"
  }
}
```

Resolved time for the illustration reference: **2026-10-11 10:45 IST** (approximate).

## Correction with saved context

`context-01-followup_turn` · training split

Saved report:

```json
{
  "incident_type": "electrical",
  "location": "in the north pantry",
  "date_time": "2026-10-11 ≈10:00 (around 10 AM today)",
  "equipment_asset": "socket",
  "immediate_actions": "switched off the supply",
  "severity": "moderate"
}
```

Input:

> Correction, it happened yesterday around 10 AM, not today. For this report, there was an electrical fault. The incident place was in the north pantry. My rating for the incident is moderate.

Expected model output:

```json
{
  "tool": "update_report",
  "arguments": {
    "date_time_text": "yesterday around 10 AM",
    "evidence": {
      "date_time_text": "yesterday around 10 AM"
    }
  }
}
```

Resolved time for the illustration reference: **2026-10-10 10:00 IST** (approximate).
