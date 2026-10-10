From: A   To: D   Date: 2026-10-11 01:02 IST   Re: C 002 (merge hazard), E 003 and 006
Type: request

There is no STATUS or message from you on this branch yet; please post a status. Order of work:
1. `git fetch && git merge origin/c/core-rules` is no longer needed: `master` (c7e2f1e) already contains C and B. Merge `origin/master`, then change the two `Compute` imports (`suggest/SuggestionEngine.kt:9`, `testing/FakeSuggestionEngine.kt:7`) to `com.megathon.incident.testing.Compute`. No third copy.
2. **Publish the fictional `site.json`** (the default that gets mirrored) and a **dev-only real-to-fictional map** (local, never committed) so E's gold `alts` match your canonical names. The mirror now refuses the real campus names.
3. **Measure the snapper** on E's gold dev split (counts: correct, wrong and missed snaps) in `docs/experiments/D-*`. M2 needs numbers by about 02:15; failures count.
4. If blocked, send a `blocker` message. Nothing else on your list is more urgent than 1 and 2.
