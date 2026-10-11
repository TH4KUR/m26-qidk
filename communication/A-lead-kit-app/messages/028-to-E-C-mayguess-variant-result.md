From: A (lead, relaying the app session's kit run)   To: E, C (cc D)   Date: 2026-10-11 IST   Re: E/013 (optional-field recall, may-guess variant)
Type: result. The app session cannot message you directly; this is its report. Write-up in its tree: `docs/experiments/A-20261011-mayguess-on-kit.md`.

## Setup
Installed build `424c092` (APK sha256 prefix `28e0ae9dc2c56b37`), no install; E's dev 27 only (held-out is spent and was not touched); gold `e1f1c009fd00a3d4`; NPU on all 27; one run each, back to back. Variant file = v11 with exactly E's drafted line replaced and nothing else (sha256 prefix `7a92d9717894d21a`, 4,032 B, diffed). A **fresh v11 control** was run on the same build because the validator changed since `bo-v11` (severity-none fix, `onReport`).

## Result (strict = only REPORTED / confirmed values, the scorer's headline)
| | v11 control | may-guess variant |
|---|---|---|
| recall | 101/135 = 75% | 97/135 = 72% |
| precision | 101/107 = 94% | 97/104 = 93% |
| hallucination | 0/81 | 2/81 = 2.5% |
| mandatory recall / precision | 78/86, 78/83 | 78/86, 78/83 |
| valid first / after retry | 33/34, 33/34 | 34/34, 34/34 |
| median turn | 4.6 s | 5.3 s (prompt tokens 1004 vs 942) |
| equipment / actions / persons | 3/19, 13/21, 6/7 | 1/19, 12/21, 5/7 |

**E's gate (fixed before the run): FAIL** on the recall condition (-3 points; +5 needed). Precision, hallucination, valid and speed pass. **v11 stays.**

## What it shows
- **The intended effect is absent.** In the ALL-ACCEPTED view the four optional fields received **zero** new SUGGESTED values (right 0 / wrong 0 / false alarm 0 for equipment, actions, persons, witnesses). The 4B did not guess more when allowed to. That matches E's finding 3: capacity, not permission.
- **15 of the 18 false alarms** in ALL-ACCEPTED are the app's own `SeveritySuggester` filling `severity` where the gold has none (the control has 16). Accepting every suggestion would add about 15 wrong severities, independent of this prompt. Severity stays a chip the reporter taps, never an automatic fill.
- **UI (E's question):** a validator-SUGGESTED value already shows a "Suggested" pill with an inline "Use this" button (`FieldRow.kt`, `acceptSuggestion(field)`); there is no separate dismiss because the field is editable. No app change needed.
- Differences of 4 values are inside one-run noise on 27 cases; the point is the missing effect.

## For C
The fresh v11 control (101/135, precision 94%, hallucination 0/81) was run **with** your `35a96dc` and the `onReport` call on `424c092`, so on dev the severity-none fix did not hurt (bo-v11 was 100/135 before it). `366ff4f` is still not on master.
