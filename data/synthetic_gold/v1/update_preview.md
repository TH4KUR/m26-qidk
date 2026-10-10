# Update-sequence preview (training examples only)

Each call uses the real saved report and up to two earlier worker utterances. Current input always answers all mandatory questions. Only changes are emitted; confirmation after two corrections emits an empty patch.

## update-severity-02

### Turn 1: initial

Input:
```text
Report so far: {}
Worker: "Please record this incident. Aman was directly affected. The affected asset was motor U010-1. We isolated the area. Harsh witnessed the incident. For this incident, someone was injured. I rate the severity critical. The incident was in Room 2. The incident happened yesterday at 4 PM."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "incident_type": "injury",
    "severity": "critical",
    "location": "in Room 2",
    "date_time_text": "yesterday at 4 PM",
    "persons_involved": [
      "Aman"
    ],
    "equipment_asset": "motor U010-1",
    "immediate_actions": "isolated the area",
    "witnesses": [
      "Harsh"
    ],
    "evidence": {
      "incident_type": "For this incident, someone was injured",
      "severity": "I rate the severity critical",
      "location": "The incident was in Room 2",
      "date_time_text": "The incident happened yesterday at 4 PM",
      "persons_involved": "Aman was directly affected",
      "equipment_asset": "The affected asset was motor U010-1",
      "immediate_actions": "We isolated the area",
      "witnesses": "Harsh witnessed the incident"
    }
  }
}
```

### Turn 2: replace

Input:
```text
Earlier the worker said: "Please record this incident. Aman was directly affected. The affected asset was motor U010-1. We isolated the area. Harsh witnessed the incident. For this incident, someone was injured. I rate the severity critical. The incident was in Room 2. The incident happened yesterday at 4 PM."
Report so far: {"incident_type":"injury","severity":"critical","location":"in Room 2","date_time_text":"yesterday at 4 PM","persons_involved":"Aman","equipment_asset":"motor U010-1","immediate_actions":"isolated the area","witnesses":"Harsh"}
Worker: "Sorry, please change my earlier answer. The affected asset was motor U010-1. For this incident, someone was injured. I rate the severity moderate. The incident was in Room 2. The incident happened yesterday at 4 PM. My earlier answer, critical, was wrong."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "severity": "moderate",
    "evidence": {
      "severity": "I rate the severity moderate"
    }
  }
}
```

### Turn 3: replace

Input:
```text
Earlier the worker said: "Please record this incident. Aman was directly affected. The affected asset was motor U010-1. We isolated the area. Harsh witnessed the incident. For this incident, someone was injured. I rate the severity critical. The incident was in Room 2. The incident happened yesterday at 4 PM."
Earlier the worker said: "Sorry, please change my earlier answer. The affected asset was motor U010-1. For this incident, someone was injured. I rate the severity moderate. The incident was in Room 2. The incident happened yesterday at 4 PM. My earlier answer, critical, was wrong."
Report so far: {"incident_type":"injury","severity":"moderate","location":"in Room 2","date_time_text":"yesterday at 4 PM","persons_involved":"Aman","equipment_asset":"motor U010-1","immediate_actions":"isolated the area","witnesses":"Harsh"}
Worker: "I checked again; use this version. The affected asset was motor U010-1. For this incident, someone was injured. I rate the severity minor. The incident was in Room 2. The incident happened yesterday at 4 PM. My earlier answer, moderate, was wrong."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "severity": "minor",
    "evidence": {
      "severity": "I rate the severity minor"
    }
  }
}
```

### Turn 4: confirm

Input:
```text
Earlier the worker said: "Sorry, please change my earlier answer. The affected asset was motor U010-1. For this incident, someone was injured. I rate the severity moderate. The incident was in Room 2. The incident happened yesterday at 4 PM. My earlier answer, critical, was wrong."
Earlier the worker said: "I checked again; use this version. The affected asset was motor U010-1. For this incident, someone was injured. I rate the severity minor. The incident was in Room 2. The incident happened yesterday at 4 PM. My earlier answer, moderate, was wrong."
Report so far: {"incident_type":"injury","severity":"minor","location":"in Room 2","date_time_text":"yesterday at 4 PM","persons_involved":"Aman","equipment_asset":"motor U010-1","immediate_actions":"isolated the area","witnesses":"Harsh"}
Worker: "Reading it back; those details are correct. Aman was directly affected. The affected asset was motor U010-1. We isolated the area. Harsh witnessed the incident. For this incident, someone was injured. I rate the severity minor. The incident was in Room 2. The incident happened yesterday at 4 PM. We will ask Tara to inspect it tomorrow."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {}
}
```

## update-witnesses-01

### Turn 1: initial

Input:
```text
Report so far: {}
Worker: "Please record this incident. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. The affected asset was pipe U057-1. We called security. Saira witnessed the incident."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "incident_type": "equipment_failure",
    "severity": "minor",
    "location": "near Lab A2",
    "date_time_text": "about five minutes ago",
    "persons_involved": [
      "Nila"
    ],
    "equipment_asset": "pipe U057-1",
    "immediate_actions": "called security",
    "witnesses": [
      "Saira"
    ],
    "evidence": {
      "incident_type": "For this incident, equipment stopped working",
      "severity": "I rate the severity minor",
      "location": "The incident was near Lab A2",
      "date_time_text": "The incident happened about five minutes ago",
      "persons_involved": "Nila was directly affected",
      "equipment_asset": "The affected asset was pipe U057-1",
      "immediate_actions": "We called security",
      "witnesses": "Saira witnessed the incident"
    }
  }
}
```

### Turn 2: replace_names

Input:
```text
Earlier the worker said: "Please record this incident. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. The affected asset was pipe U057-1. We called security. Saira witnessed the incident."
Report so far: {"incident_type":"equipment_failure","severity":"minor","location":"near Lab A2","date_time_text":"about five minutes ago","persons_involved":"Nila","equipment_asset":"pipe U057-1","immediate_actions":"called security","witnesses":"Saira"}
Worker: "Actually, I got that detail wrong. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. Sara witnessed the incident. Saira was not a witness."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "witnesses": [
      "Sara"
    ],
    "evidence": {
      "witnesses": "Sara witnessed the incident"
    }
  }
}
```

### Turn 3: replace_names

Input:
```text
Earlier the worker said: "Please record this incident. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. The affected asset was pipe U057-1. We called security. Saira witnessed the incident."
Earlier the worker said: "Actually, I got that detail wrong. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. Sara witnessed the incident. Saira was not a witness."
Report so far: {"incident_type":"equipment_failure","severity":"minor","location":"near Lab A2","date_time_text":"about five minutes ago","persons_involved":"Nila","equipment_asset":"pipe U057-1","immediate_actions":"called security","witnesses":"Sara"}
Worker: "Sorry, please change my earlier answer. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. Sana witnessed the incident. Sara was not a witness."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {
    "witnesses": [
      "Sana"
    ],
    "evidence": {
      "witnesses": "Sana witnessed the incident"
    }
  }
}
```

### Turn 4: confirm

Input:
```text
Earlier the worker said: "Actually, I got that detail wrong. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. Sara witnessed the incident. Saira was not a witness."
Earlier the worker said: "Sorry, please change my earlier answer. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. Sana witnessed the incident. Sara was not a witness."
Report so far: {"incident_type":"equipment_failure","severity":"minor","location":"near Lab A2","date_time_text":"about five minutes ago","persons_involved":"Nila","equipment_asset":"pipe U057-1","immediate_actions":"called security","witnesses":"Sana"}
Worker: "Yes, that is the final version. For this incident, equipment stopped working. I rate the severity minor. The incident was near Lab A2. The incident happened about five minutes ago. Nila was directly affected. The affected asset was pipe U057-1. We called security. Sana witnessed the incident."
```

Gold patch:
```json
{
  "tool": "update_report",
  "arguments": {}
}
```
