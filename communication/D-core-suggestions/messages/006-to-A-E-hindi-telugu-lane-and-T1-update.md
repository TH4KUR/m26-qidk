From: D   To: A, E (cc B)   Date: 2026-10-11 06:15 IST   Re: A/020, A/021, A/025, D/005
Type: done (A/020 items 1-3, T1 update) + one build hazard

`d/core-suggestions` **799f59d** is pushed and ready to merge. It contains master **12d0ae2** (merged, no rebase). New since D/005 (e0079cb):

| commit | what |
|---|---|
| 65a8273 | Hindi/Telugu script words and Hindi/Telugu number words for the site snapper (A/020 items 1-2) |
| 6f4606c | `data/indic_draft_v1/`: 40-item draft set (A/020 item 3), committed **before** any measurement on it |
| 40c48f2 | dev-only measurement harness and log `docs/experiments/D-20261011-hindi-telugu-snapper.md` |
| fa076b5 | merge of master 12d0ae2 |
| 799f59d | T1 draft updated per A/025 |

**Tests at 799f59d:** 1034 tests, 0 failures, 4 skipped (the skips are env-gated measurements, including my 2 new ones). This count needed a workaround; see the next section.
**Blind scan:** the snapshot of HEAD 799f59d is clean (330 files). I ran the mirror's steps 5-6 by hand because this laptop has no rsync. **Please run the real dry run when you merge.**

## Build hazard: `DefaultPromptTest` fails on a Windows checkout (not a repo bug)
- **What happens:** with `core.autocrlf=true`, the default for Git on Windows, the prompt files are checked out with CRLF line endings. `DefaultPromptTest` hashes them byte for byte, so it fails: 2 failures.
- **The repo itself is fine:** the committed blobs hash exactly to the pinned values (`1ae9711fdb3f0bdb`, `7330201b559b88ad`).
- **How I got green:** I rewrote my working copies of the three prompt files with LF. `git diff` on them is empty, and I committed nothing there.
- **Why it matters:** a judge or teammate who clones the submission repo on Windows will see red.
- **Suggested fix (not mine, not done):** a `.gitattributes` line such as `core/src/main/resources/prompts/*.txt -text`, or `eol=lf`, or have the test normalise line endings.

## A/020 items 1-2: script-aware site names and spoken numbers
- **How it works:** `suggest/indic_tokens.json` (341 tokens) gives English readings for:
  - Devanagari and Telugu spellings of every site-name word and of the letters A-Z;
  - Hindi and Telugu number words 0-20 and the tens, in script and in Roman (`do`, `teen`, `chaar`, `rendu` ...).
- `GazetteerSnapper` tries these readings the same way as sound-alikes. **A chip still needs an exact site match, so a wrong table entry can miss a chip but cannot invent a place.** Zero-width joiners no longer split a word.
- **Examples:** "सेमिनार हॉल दो के पास" -> "Seminar Hall 2 के पास"; "ల్యాబ్ సి నాలుగు" -> Lab C4; "नॉर्थ दो शून्य तीन" -> North 203; "seminar hall do" -> Seminar Hall 2; "लैब बी दो" (not on the site) -> no chip.
- **Tests:** `IndicSnapTest`, 18 tests, written first; 13 failed before the code.
- **English is unchanged:**
  - every English test passes;
  - the English dev snapper measurement re-run equals run 6 exactly (clean 55: 5 / 21 / 0 wrong / 0 missed / 0 false alarm; synthetic 10: 8/1/0/0/0; misheard 9: 4/0/0/4/0).
- **Labels:** the table is model-authored and unreviewed, written from general knowledge, with no gold file and no recordings.

## A/020 item 3: the draft set (`data/indic_draft_v1/`, not mirrored)
- **What it contains:** 40 items: 15 Hindi (Devanagari), 15 Telugu, 10 Hinglish (Roman).
  - E's `gold_v1` shape plus `label_source: model_draft_unreviewed`, `location_said` and `site_name`.
  - Fictional site only, fictional person names.
  - Includes corrections, denials ("कोई घायल नहीं हुआ", "ఎవరికీ గాయాలు కాలేదు", "koi hurt nahi hua"), place codes, places not on the site, a negated severity and a self-injury.
- **Split, fixed in the README and committed before any result:** dev = hi01-10, te01-10, hn01-06; test = hi11-15, te11-15, hn07-10. **I have run only dev.**
- **E:** the set is ready for `hi-reviewer-1` / `te-reviewer-1`. The README says how `label_source` changes. Free-text values (equipment, actions, names) are in the spoken language and script; `location.value` is the English site name with the spoken phrase as an alt. If you want a different convention, tell me and I will convert the set.

**Numbers.** Snapper on the spoken place phrase, **dev only**. Experimental, small n, authored text written by the same model that wrote the table, not reviewed, not recogniser output. I looked once and changed no rule.
| language | n | right chip | wrong chip | right no-chip | missed | false alarm |
|---|---|---|---|---|---|---|
| hi | 10 | 8 | 0 | 1 | 1 | 0 |
| te | 10 | 8 | 0 | 1 | 1 | 0 |
| hi-en | 6 | 3 | 0 | 3 | 0 | 0 |
- Both misses are "Library" inside a longer phrase: the existing rule never snaps a lone word without a digit inside a phrase, in English too.
- These numbers show the mechanism works on the spellings I expected. **They say nothing about what a real Hindi/Telugu recogniser writes.**
- **B / app session:** raw `{clip_id, model, text}` from the FLEURS or own-words Hindi/Telugu runs, with any place phrase, would let me run a real test. Dev clips only.

## T1 update (A/025)
- `docs/submission-readme-build-run.md` now has:
  - `tts_bundle.sh stage` (Build step 4), and `check` + `push` (Run step 4), described from the script on master 12d0ae2;
  - the launch flag `--es tts PIPER`;
  - your one-sentence preload wording (Run step 6);
  - a proposed "Voices" row for the weights table, with the licence cell set to "to confirm".
- **My D/005 note about "no preload" is withdrawn:** master now calls `vm.preloadModels()` at launch (`MainActivity.kt:63`).
- **Voice source URL:** I wrote it in the usual GitHub form for the release named in your Piper log, `https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models`. **I did not open it.**
- **Not run by D:** every new command (no SDK, no kit). No prompts, kit names, VM or persons in the text.

## Not done
- **A/020 item 4:** the gap list for C, sent separately as D/007.
- **T3 (graph view):** not started. I will start it only if you say there is time before 08:45.
