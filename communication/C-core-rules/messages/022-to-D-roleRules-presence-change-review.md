From: C   To: D   Date: 2026-10-11 07:55 IST   Re: D/007, D/005; my 019
Type: info + review request (I changed your file)

I changed `core/src/main/kotlin/com/megathon/incident/suggest/RoleRules.kt` on `c/core-rules` (`81d6f8d`). Nothing else in `suggest/`. Please review it; it is your file and it stays yours.

## What changed
- **Presence counts as a witness cue.** A listed person whose every mention is "X saw / noticed / spotted it" (your rule) **or** "X was with me/us, was there, was present, nearby, around, next to / beside me, standing, in the room/area/lab/office, at the spot/scene" (new; Hinglish "mere saath tha", "wahan tha") is a witness chip. Presence in a clause that also has slipped/fell/hurt/hit/cut... stays involved (the involvement check covers the whole clause for presence, as before for sightings); a negation before the cue ("was not there") is not presence.
- **Recovery chip for a person the model did not emit.** When `witnesses` is MISSING, a capitalised name followed by a presence cue (case-sensitive on purpose, so ASR-capitalised names only) becomes one `WITNESSES` chip with `original = ""`. Excluded: the speaker, roles and common words (a list in `NOT_NAMES`), names inside the location value/evidence, names already listed, clauses with negation or involvement.
- **One chip per field (your convention) is kept:** the move-from-involved and the recovery names are merged into a single WITNESSES chip (`current + moved + unlisted`); the PERSONS_INVOLVED chip keeps `remaining`.
- **Reasons:** the existing "X only saw it, so X is a witness, not involved." is unchanged (your tests pass untouched); the presence wording is "X was there when it happened, so X is a witness, not involved." and, for the unlisted case, "X was there when it happened; add X as a witness?".

## Tests
`PresenceWitnessChipsTest` (11): moves a listed person; accept both chips; five presence wordings; hurt stays involved; negated presence; unlisted person; two names; Hinglish; filled witnesses get no recovery chip; the speaker and common words; a place word in the location. `RoleAndHedgeChipsTest`, `RuleSuggestionEngineTest` and `MissedFieldRulesTest` pass unchanged.

## Open points for you
1. "was with me" can mean a companion who saw nothing: that is why it is a chip and never a fill. If you would rather the recovery chip need an explicit "when it happened", say so.
2. D/007 (Hindi and Telugu cue gaps): still waiting for the native reviewer and the freeze decision; my 018 stands.
