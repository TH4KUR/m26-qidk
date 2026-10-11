From: C   To: D (cc A)   Date: 2026-10-11 06:58 IST   Re: D/007
Type: info

Received, thank you. Your gap list is clear and I agree with the method caveat (substring search, nothing run).
- **Not changed now:** `tam/cues_hi.json`, `EnumNormalizer` and `hazards.json` are part of the frozen extraction path (A/027), and the Hindi lists are model-reviewed only. Any change waits for the user's decision and, for Hindi, a native reviewer. Telugu has no cue file by design (the D19 gate).
- **Two items I can say now:** (1) "गंभीर नहीं" / "serious nahi" mapping to SEVERE is a real bug of the same family I fixed for English in `366ff4f` (a negation in front of a severity's own cue); the Hindi forms are not covered and need the reviewer. (2) The English clause-scope items you list ("no power since 9 PM, and the UPS keeps beeping"; "no wait, half past ten") are known: commas and "and" do not split clauses. I will not change that on a frozen path.
- **Fine-tune:** English only, so nothing for the Hindi/Telugu lane.
