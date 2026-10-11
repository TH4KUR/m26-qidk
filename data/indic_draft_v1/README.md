# Hindi / Telugu / Hinglish draft set v1 (A/020 item 3)

**Experimental lane (D19). Model-authored, NOT reviewed.** Every item has `label_source: model_draft_unreviewed`.
Nothing here is a claim of accuracy until a native speaker has checked it (E arranges reviewers by role,
`hi-reviewer-1`, `te-reviewer-1`; never names). A reviewer changes `label_source` to `human_checked` only for items
they passed, and keeps a note on items they fixed.

- `indic_draft_v1.jsonl`: 40 items, 15 Hindi (Devanagari, `hi01`-`hi15`), 15 Telugu (`te01`-`te15`), 10 Hinglish in
  Roman script (`hn01`-`hn10`, `language: hi-en`). Plain first-person worker speech on the **fictional** site
  (`core/src/main/resources/site/site.json`). Includes self-corrections (`correction`), denials ("nobody was hurt",
  `denial`), place codes said as letters and number words (`place_code`), places not on the site (`unknown_place`),
  a negated severity (`negated_severity`) and self-injury (`self_injury`).
- **Shape:** E's `gold_v1` item shape (`id`, `split`, `source`, `language`, `turns`, `gold{...}`, `missing_mandatory`,
  `tags`) plus three fields: `label_source`, `location_said` (the place phrase exactly as spoken) and `site_name` (the
  English site entry it names, or null for a place not on the site). In `gold`, `location.value` is the English site
  name with the spoken phrase as an alt; free-text fields (equipment, actions, names) are in the spoken language and
  script; enums are the English wire values.
- **Split, fixed before any result was looked at (by number, per language):** `dev` = `hi01`-`hi10`, `te01`-`te10`,
  `hn01`-`hn06`; `test` = `hi11`-`hi15`, `te11`-`te15`, `hn07`-`hn10`. D measures on `dev` only; `test` is for one
  later run.
- **Not mirrored** (outside `app/`, `core/`, `tools/`). Fictional person names only.
- Authored by D on 2026-10-11 from general knowledge; not derived from any gold file or recording.
