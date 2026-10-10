# Incident Assistant: UI/UX design brief

Owner: session A (Track A, the app). Status: working brief, written before the screens. Every screen decision should be traceable to a line here.

## 1. Who it is for, and what it is for

Two audiences use the same screen, and the design has to serve both without cluttering either.

| Audience | Situation | What they need |
|---|---|---|
| **The worker** (primary) | Something just happened. Stressed, maybe one hand busy, maybe a noisy room, maybe not a native English speaker. Does not want to fill a form. | To say what happened in their own words, be asked only what is missing, and trust what gets sent. |
| **The judges** (secondary, 3 minutes, a projector) | Scoring on-device AI, natural speech, recognition accuracy, extraction accuracy and field mapping. | To *see* the AI working on the device: what was heard, which words support each field, where each stage ran, that nothing left the device. |

**Core job:** speak, see the report fill itself with the words that justify it, answer what is missing, check, send.

**Rule that follows:** the worker's path is calm and nearly empty. The proof for the judges is always **one tap away** and a **single quiet line** on the main screen; it never competes with the task.

## 2. Principles (and what each one forbids)

1. **Speak, do not type.** Every step is reachable by voice or one tap. Typing is the fallback, never the path. *Forbids:* a form that opens first.
2. **One thing at a time.** One question at a time, one primary action per screen. *Forbids:* two filled buttons side by side; a wall of fields before anything is known.
3. **Show your work.** Every value carries where it came from (heard, suggested, confirmed) and the exact words that support it. *Forbids:* a filled field with no evidence.
4. **The person decides.** The AI proposes, the person disposes: edit any value, undo any change, confirm before sending. Submit is never blocked by a pending suggestion. *Forbids:* silent rewrites; auto-submit.
5. **Honest labels.** Simulated is labelled simulated, CPU is labelled CPU, an unknown stays unknown. *Forbids:* any scripted or fake result that could pass as live recognition.
6. **Less, but better.** No decoration that carries no information: no gradients, no glow, no looping animation except the listening indicator (which becomes static under reduce-motion).

## 3. The flow

```
Ready ──tap mic──> Listening ──tap stop──> Working (heard… → reading…)
                                                  │
                                                  v
                          Report (fields fill, evidence highlighted)
                             │  asks ≤ 2 questions, by voice and on screen
                             │  answer by voice or by tapping a reply
                             │  "Actually it was Room 3" = correct by voice
                             v
                        Check & send  ──confirm──>  Sent (summary read aloud)
```

| Screen | Purpose | Primary action |
|---|---|---|
| **Ready** | Invite speech. Show the radios-off state and one example sentence. | Big mic button |
| **Listening** | Make it unmistakable that the device is recording, and for how long. | Stop |
| **Working** | Say what is happening in two honest steps. | none (it is short) |
| **Report** | The conversation and the live form. Transcript on top with evidence highlights, the open question, then ten fields. | Answer / add by voice |
| **Check & send** | Read-only summary. The assistant-written parts are labelled. This is the human-in-the-loop gate. | Send report |
| **Sent** | Confirm it is saved on the device, read the summary aloud. | New report |
| **How this ran** (sheet) | Proof: stage by stage compute, timings, tool-call log, network. For judges and auditors. | none |

## 4. Visual system

**No gradients, no glow, no pure black or white, spacing on a 4 dp scale, hairlines for structure, tonal surfaces for things that float.**

### Colour (contrast measured, WCAG 2.1)

Light / dark. Text pairs all pass 4.5:1; most pass 7:1.

| Role | Light | Dark | Notes |
|---|---|---|---|
| canvas | `#F4F3EF` | `#101214` | app background, warm off-white / near black |
| surface | `#FBFAF7` | `#181B1E` | cards |
| ink | `#17191C` | `#ECEAE6` | 15.9:1 / 15.6:1 on canvas |
| ink 2 | `#4A4F57` | `#B8BCC2` | secondary text, 7.4:1 / 9.8:1 |
| ink 3 | `#626872` | `#969CA5` | tertiary text **and every interactive outline** (5.1:1 / 6.8:1), so controls meet the 3:1 non-text rule |
| line | `#C9C6BD` | `#3A4047` | **decorative hairlines only** (1.5:1), never the only boundary of a control |
| accent | `#0B6E66` | `#58C4B7` | brand and primary action; 5.5:1 / 8.9:1 on canvas |
| accent container | `#D5EBE7` | `#163B37` | confirmed / on-device chips, text 7.6:1 |
| attention | `#F8E7C8` on `#7A4300` | `#3A2A12` on `#F2BE72` | suggested, needed, unsure |
| danger | `#F9DEDA` on `#9F1D15` | `#3F1512` on `#FFB4AB` | urgent only; **red is never the brand** |
| simulated | `#F8DCE7` on `#9A0F45`, solid `#B5124F` | `#3F1226` on `#FFB1CC` | loud on purpose |

**Colour is never the only signal.** Every status has an icon and a word as well as a colour.

### Type, space, shape, motion

- **Type** (system font, so nothing to download): display 32/40 semibold, title 22/28 semibold, body 17/26 regular (larger than default: read under stress), label 14/20 medium, caption 12/16. Hierarchy by weight and colour first, size second. Numbers in timings use tabular figures.
- **Space** on a 4 dp scale: 4, 8, 12, 16, 24, 32, 48. The gap inside a group is smaller than the gap between groups.
- **Targets:** 48 dp minimum, 56 dp for primary actions, 96 dp for the mic.
- **Shape:** radius 12 (chips, fields), 20 (cards), full (mic, pills).
- **Elevation:** hairline border for structure; a tonal step for sheets. No stacked border + shadow + fill.
- **Motion:** 120 to 250 ms, ease-out entering, ease-in leaving, `transform` and `opacity` only. A new value fades in with a short evidence highlight sweep. **Reduce motion** (animator scale 0) removes the pulse and the sweep and keeps the state change.
- **Haptics:** a light tick on start and stop, a confirm on send. Never for decoration.

## 5. The report screen, in detail

- **Transcript card:** what was heard, with the words that support the selected field highlighted. Tapping a field highlights its evidence (the judges' "evidence highlight" view; the worker's "why did it write that").
- **Open question:** one card with the question in plain words, the reply chips that answer it (for example `Minor` `Moderate` `Severe` `Critical` `No harm`), and a mic to answer by voice. Spoken aloud as well.
- **Ten fields**, in a fixed order, mandatory four first: incident type, location, date and time, severity, then people, equipment, what was done, witnesses, and the two the assistant writes. Each row: label, value, status, and an edit affordance. An empty mandatory field says **Needed**, not blank.
- **Status vocabulary** (word + icon + colour): **Heard** (said by the worker), **Suggested** (the assistant's guess, needs a tap), **Confirmed** (the worker confirmed or typed it), **Unsure** (heard, but not supported by the words), **Needed**, **None** (the worker said there was none).
- **Priority** appears only when it rises above routine, as a banner with its reasons. It can be overridden by the worker, and the override is journaled. When it is urgent the first line is "If anyone is in danger, call 112."
- **Corrections:** voice ("Actually it was Conference Room 3") or tap a field to edit. The previous value stays in the journal. Every change can be undone for a few seconds.

## 6. Trust, mapped to patterns

| Pattern | How it shows up |
|---|---|
| **Disclosure** | Assistant-written fields carry "Written by the assistant"; the AI's actions are named with verbs ("Filled from what you said"). |
| **Footprints** | Evidence under every value; the tool-call log (`update_report`, `ask_user`, `submit_report`); model and compute per stage. |
| **Consent** | Recording starts only on a tap, shows a persistent "Recording on this device" bar and a timer, and has a haptic. No hidden or automatic recording. |
| **Caveat** | Specific, at the moment of decision: on Check & send, "The assistant filled some fields from your voice. Check them before sending." Not a footer. |
| **Data ownership** | One plain line: "Stays on this device. Nothing is sent anywhere." backed by a measured "bytes sent: 0" and the radios-off badge. |
| **Verification** (governor) | Check & send is a real gate, with the assistant's guesses visibly marked. |
| **Controls** (governor) | Stop is always in the same place while listening or working; edit and undo everywhere. |

## 7. Accessibility commitments (WCAG 2.1 A and AA)

- Text contrast 4.5:1 or better, interactive outlines 3:1 or better (section 4).
- No colour-only meaning; icons and words accompany every status.
- Minimum 48 dp targets; text scales to 200% without losing content or function.
- Every control has an accessible name that includes its visible label; headings are marked; status changes ("Listening", "Heard it", "Saved") are announced as live regions without moving focus.
- Reduce-motion respected; nothing blinks or auto-advances.
- Errors say what happened and what to do next, in words.
- Large mic button and reply chips so the primary path needs no fine motor control.

## 8. What the judges see (and how it stays honest)

The **How this ran** sheet shows, per stage (hear, read, speak), the engine, where it ran (NPU, CPU, hybrid, or **SIMULATED**), and the time. It also shows the tool-call log, whether radios are off, and `TrafficStats` bytes sent by this app. Text-to-speech uses the system voice and is labelled **CPU**. Nothing is labelled NPU unless the engine reported NPU.

## 9. Interim pieces, to be replaced (not hidden)

- **Speech:** until the real engines land, a scripted demo speech engine drives the screens and is labelled **SIMULATED** on screen. It is debug-only and the release guard refuses it.
- **Merge, follow-up planner, derived fields, storage:** thin interim versions behind small interfaces in `session/`, replaced by the owners' versions (suggestions, planner, storage) without changing the screens.
- **Language lane** (Hindi, Telugu) is gated by measurement and is not in this UI yet.
