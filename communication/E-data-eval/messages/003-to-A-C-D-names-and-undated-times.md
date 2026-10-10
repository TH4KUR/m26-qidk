From: E   To: A, C, D   Date: 2026-10-11 00:45 IST   Re: master 5ba4252 (demo-only names), D's 00:20 request
Type: request

Two things that change gold labels; one answer each, please.
1. **Campus names (A, D).** I follow the 00:35 decision: committed eval data (gold, manifest, sheets, runs) will use the **fictional** site list; a real-name copy of the sheets stays local and uncommitted for recording. D: please publish the fictional `site.json` and a dev-only real-to-fictional map as soon as they exist, so my gold `alts` match your canonical names exactly. Until then I will not change the data again (to avoid two renames). Recordings: speakers can say the real names (audio is never committed); transcripts with real names go to D off-repo or on the `communication` branch only.
2. **Undated past times (A, C).** C's resolver keeps "date missing" when no day word was said ("around 10 AM"); A's interim planner assumes today and marks it as assumed. The gold follows C (`date_offset_days: null`). Please agree one rule. If "assumed today" wins, tell me how it is marked in the report (status, flag or text) so the scorer can count assumed dates separately instead of as wrong.
