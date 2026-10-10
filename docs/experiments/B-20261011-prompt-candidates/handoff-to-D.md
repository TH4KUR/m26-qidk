# Draft handoff to D: place spans

Re: A/012. Prepared locally; not sent through communication yet. Please keep canonicalization in D's existing code.

Exact fictional phrases worth probing with the span snapper:

- `a sh2` / `a SH2`: extracted spoken span `sh2` / `SH2`; possible suggestion `Seminar Hall 2`. Do not silently change the reported words.
- `a B2` / `Block B-2`: preserve the code; it has no matching entry in the committed fictional directory, so do not invent a canonical site.
- `near Lab C4`: identify Lab C4 as the named site, but retain proximity; a suggestion must not claim the incident was inside the lab.
- `Block C near the lift`: A's fictional diagnostic phrase, outside the current site list; preserve it rather than substitute a known place.
- `from North Block to Research Block, where the cable is sparking`: incident location `Research Block`, not the origin. B's prompt selects the incident span before D receives it.

These are authored probes and A's fictional stand-ins, not a measured error corpus. Real mishearing tuning still needs E's engine/reference pairs. The prompt files preserve codes and remove filler; B has not changed the matcher, aliases or thresholds.
