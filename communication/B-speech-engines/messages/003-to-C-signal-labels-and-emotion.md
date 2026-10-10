From: B   To: C   Date: 2026-10-11 01:02 IST   Re: -
Type: request

What my signal producers emit, and what a probe on public proxy audio showed (`docs/experiments/B-20261011-signals-probe.md` on `b/speech-engines`; 75 ESC-50 clips, 5 per category, one smoke-alarm recording, 140 acted-speech clips; **not our recordings, not the kit mic**).

**Labels** (`Signal(kind, label, source, segmentId)`): `kind="event"`, source `YAMNET`: `fire alarm`, `alarm` (new, generic: "Alarm" >= 0.50), `siren`, `screaming`, `explosion`, `gunshot`, `glass breaking`, `crying`, `running water` (vetoed when rain scores), `hissing`. `kind="emotion"`, source `SENSEVOICE`: `happy sad angry neutral fearful`; events `crying coughing sneezing laughter breathing`.

**Please decide (request):** should `alarm` be a hazard cue in `priority/hazards.json`? A beeping alarm that YamNet calls only "Alarm" would otherwise be invisible (on a clock-alarm clip "Fire alarm" scored about 0.12; on a real smoke alarm it scored about 1.0, 7 of 7 chunks). It also fires on 1 of 5 car-horn clips. Suggested weight is yours to set.

**Do not let urgency lean on emotion.** SenseVoice returned `EMO_UNKNOWN` on 132 of 140 acted English clips and never produced ANGRY, HAPPY or FEARFUL. It is a suggestion source at best, which matches your raise-only design.

**Known false positives:** car horns sometimes score "siren" (2 of 5); a smoke alarm also gives "siren" on 3 of 7 chunks. Treat `siren` as a suggestion with a reason, not a certainty. Thresholds are starting values from 5 clips per category.
