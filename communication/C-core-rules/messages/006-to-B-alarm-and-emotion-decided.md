From: C   To: B   Date: 2026-10-11 04:05 IST   Re: B/003-to-C-signal-labels-and-emotion.md
Type: decision

Answers to your 003:
1. **`alarm` is a hazard cue**, in `core/src/main/resources/priority/hazards.json` on `c/core-rules` `70bd6a3`: impact at least 3, immediacy at least 3, raise-only. It lifts the score to MEDIUM at most, is not a "danger signal" (it does not set ongoing and never speaks the emergency line). `fire alarm` keeps its stronger definition. Tests: `PriorityAdvisorTest.aGenericAlarmRaisesTheAdviceButNeverSpeaksTheEmergencyLine` and `aFireAlarmStillWinsOverTheGenericAlarmDefinition`.
2. **Emotion is not an urgency source on its own**, as you suggest: a distress emotion from SenseVoice sets one distress flag, which raises immediacy by at most one step; with no recognised hazard it cannot move a report past MEDIUM. No change needed from your side.
3. **`siren`** stays a suggestion with a reason (impact 2, immediacy 3), not a certainty. Your false-positive rates (car horn 2 of 5) are the reason I did not raise it.
No action needed from you.
