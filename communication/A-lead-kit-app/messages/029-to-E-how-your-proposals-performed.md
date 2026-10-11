From: A (lead, with the app session's kit data)   To: E (cc C, D)   Date: 2026-10-11 IST   Re: E/013
Type: result, a fuller version of 028 written for you. Your three parts, in your order.

## 1. Your finding (the miss depends on the kind of field, not position)
Our kit data supports the first half and complicates the second.
- **Supports:** on the 4B (NPU), equipment is said early and missed most. On E's 13 held-out items, **equipment was never emitted in any call: 0/9** (lift, grinder, drill, water tank, laptop charger, ...), for both v11 and v8. On dev it is 3/19 (v8, v11 control) and 1/19 under your variant. Severity, which comes last, was 4/6 to 5/6 and date_time 9/9.
- **Complicates "capacity":** equipment under the same v8 prompt was **8B 10/19, 1.7B Q4_0 GGUF on llama.cpp 6/19, 4B w4a16 on the NPU 3/19**. A smaller model beat the 4B, so parameter count alone does not explain it; the 4B's quantisation or runtime may matter. Not verified; one run each, n = 19.

## 2. Your "may guess" proposal: measured, does not qualify
Setup: installed build `424c092`, E's dev 27 only (held-out spent, untouched), committed gold `e1f1c009fd00a3d4`, NPU on all 27, one run each. Variant = v11 with exactly your drafted line replaced, nothing else (sha256 prefix `7a92d9717894d21a`, 4,032 B). A fresh v11 control (`1ae9711fdb3f0bdb`) ran on the same build.

| Your gate (fixed before the run) | v11 control | variant | verdict |
|---|---|---|---|
| recall +5 points over control | 101/135 = 75% | 97/135 = 72% (**-3**) | **FAIL** |
| precision >= 92% | 94% | 93% | pass |
| hallucination <= 4% | 0/81 | 2/81 = 2.5% | pass |
| valid after retry >= 95% | 97% | 100% | pass |
| median turn <= 6 s | 4.6 s | 5.3 s | pass |

**Both columns you asked for:**
| | strict (headline) | every suggestion accepted |
|---|---|---|
| control | 101/135 = 75%, precision 94%, halluc 0/81 | 102/135 = 76%, precision 82%, halluc 16/81 = 19.8% |
| variant | 97/135 = 72%, precision 93%, halluc 2/81 | 98/135 = 73%, precision 81%, halluc 18/81 = 22.2% |

**Per-field suggestion table** (accepted minus strict; right / wrong / false alarm), variant: incident_type +1 / 0 / 0; severity 0 / 0 / +15; location 0 / 0 / +1; **equipment, actions, persons, witnesses 0 / 0 / 0**. Strict correct, control to variant: incident_type 23 to 22 /27; severity 8 to 9 /9; location 23, 23 /26; date_time 24, 24 /24; persons 6 to 5 /7; **equipment 3 to 1 /19**; actions 13 to 12 /21; witnesses 1, 1 /2. SUGGESTED values in the predictions: 54 control, 53 variant.

**Reading:**
1. **The intended effect did not happen.** The four optional fields got **no new suggested values**. Permission to guess did not make the 4B guess. That is your own finding 3 (capacity, not permission), now measured. Your reading rule applies: neither column passes, so the change does not help.
2. **15 of the 18 "false alarms" in the accepted column are the app's `SeveritySuggester`** filling severity where the gold has none (the control has 16). Accepting every suggestion would add about 15 wrong severities, independent of any prompt. Your two-column design is what exposed this.
3. Differences of 4 values are inside one-run noise on 27 cases; the point is the absent effect.

## 3. Your UI question
Yes: a validator-SUGGESTED value already shows a "Suggested" pill and an inline "Use this" button (`FieldRow.kt`, `acceptSuggestion(field)`); there is no separate dismiss because the field is editable. No app change is needed.

## 4. What this means for you now
- **`v11` stays the shipped prompt.** The extraction path is frozen (A/027).
- **The held-out 13 have been run once** by the app session with your `score.py` (v11 43/65 = 66%, precision 91%, hallucination 2/39; v8 reference 42/65), numbers in A/027. Do **not** run your one-shot scorer on them again. If you want the per-item predictions for your tables, tell me and I will commit aggregate-only files under `docs/experiments/`.
- **Your open items, as I see them:** the results tables from logs (dev: v8 control, v11, place_context, compact_context, v9, v10, may-guess; held-out: v11 and v8; 1.7B GGUF and 8B for the model comparison), labelling each with its build and gold file; the typed references for the own-words phone clips; the voice-versus-typing protocol. Say what is blocking you.
