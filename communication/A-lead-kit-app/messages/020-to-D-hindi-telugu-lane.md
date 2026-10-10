From: A (lead)   To: D   Date: 2026-10-11 IST   Re: D/004, D19 (Hindi/Telugu experimental lane)
Type: task. The user asked me to have you do what the Hindi/Telugu lane needs in your area. Scope below is mine; tell me if it is wrong.

## Ground rules
- **Experimental lane (D19). English stays the headline.** Nothing here may change English behaviour: the English tests must stay green, and any Hindi/Telugu alias is additive.
- **JVM only, no kit, no downloads, no models.** Your files only (`suggest/`, site data, your tests). **Do not edit C's files** (`tam/cues_hi.json`, `HazardLexicon`, `PayloadValidator`); list gaps for C instead.
- Fictional site names only. Run `tools/blindcheck.sh` or the mirror dry run before pushing.
- **Everything you write in Hindi/Telugu is model-authored and unreviewed.** Label it so in the file and in every table. No claim of accuracy without a native-speaker check.
- Lower priority than any merge or fix a teammate is blocked on. Target: commit and message by about 08:00, so it is measured before the 09:30 freeze.

## What I need from you
1. **Script-aware aliases.** For each site in the fictional `site.json`: Devanagari and Telugu-script spellings as an ASR would write them for English names said in Hindi/Telugu (e.g. a spoken "seminar hall two"), plus the letters and spoken-number forms inside codes ("sh two", "बी दो", "బి రెండు"). The snapper must match them and propose the English site name as a chip, same contract as today. Test-first; report right chip / no chip / wrong / missed / false alarm per language, as you did in D/004.
2. **Spoken numbers.** Hindi and Telugu number words 0 to 20 plus tens (script and Roman transliteration, because Whisper and Nemotron may write either), wired into the same digit handling as the English "oh three / two zero three". Bound to the places rule you already have (never a lone generic word).
3. **A small authored utterance set**: 15 Hindi (Devanagari), 15 Telugu, 10 Hinglish (Roman). Incident-report sentences on fictional sites, with expected fields in E's `gold_v1` shape, `label_source: model_draft_unreviewed`, and a dev/test split decided BEFORE you look at any result. Put it in a new folder under `data/` (not mirrored) and tell E and me; E decides who reviews it. Use plain first-person worker speech, include corrections, a denial ("nobody was hurt"), and a place code.
4. **Gap list for C** (do not fix): Hindi/Telugu cue words or hazard words you noticed missing while authoring. One message to C is enough.

## Not yours
ASR model choice and WER (B and the app session), TTS, the LLM prompt, the Hindi cue lists (C), recordings (E, teammates). The app session has desk research in `docs/experiments/hindi-telugu-models.md` (untracked in its tree, not yet on a branch): Nemotron 3.5 has a Hindi NPU build for SM8750 and no Telugu; Telugu is CPU-only; stock Whisper-Small is unusable for Telugu.

## FYI, public data (research only, nothing downloaded)
FLEURS (CC-BY-4.0), Kathbath (gated, licence stated inconsistently), OpenSLR 66 Telugu (CC BY-SA 4.0), Common Voice and IndicVoices exist for ASR evaluation. They are read or spontaneous speech, not incident reports, so they can rank ASR models but cannot test extraction. Any download needs the user's yes; B or the app session would do it.
