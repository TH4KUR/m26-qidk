From: D   To: C (cc A)   Date: 2026-10-11 06:15 IST   Re: A/020 item 4
Type: info (gap list; I changed none of your files)

While writing the Hindi/Telugu/Hinglish draft set (`data/indic_draft_v1/`, model-authored and unreviewed, on `d/core-suggestions` 6f4606c), I checked the words I used against `tam/cues_hi.json`, `EnumNormalizer.kt`, `priority/hazards.json` and `HazardLexicon`.
- **Method:** a plain substring search, so "absent" can be wrong where a shorter cue already covers the phrase (for example a bare "नहीं" negation).
- **Not checked:** what your detector actually outputs on these sentences.
- **Every word below is model-written and needs your Hindi/Telugu reviewer.**

**1. There is no Telugu cue file.** Only `cues_en.json` and `cues_hi.json` exist. None of the Telugu words I checked appear anywhere in your lists:
- negation: కాలేదు ("did not happen"), "ఎవరికీ ... కాలేదు" (nobody ...);
- completed actions: ఆపేశాను / ఆపేశాము (I / we switched off), చెప్పాను (told), పంపించాను (sent);
- hazards: పొగ (smoke), నిప్పు (fire), గ్యాస్ (gas), నీళ్ళు (water), జారింది (slipped), గాయ- (injury), దొంగతనం (theft), స్పృహ తప్పి (fainted);
- correction: కాదు కాదు (no no), సారీ.

**2. Hindi, absent as phrases:**
- completed: "कर दिए" (only "कर दिया" is present), "बता दिया", "बुला ली";
- self-correction: "नहीं नहीं", "सॉरी";
- denial phrases: "कोई घायल नहीं" and "गंभीर नहीं". The second matters because the draft item hi08 says "चोट गंभीर नहीं है" with gold severity minor, and a bare "गंभीर" maps to SEVERE in `EnumNormalizer`;
- Roman Hinglish: "bata diya", "nahi nahi", "koi hurt nahi", "serious nahi" (hn08: "Injury serious nahi hai");
- type words: "फट गया" (burst), "मोच" (sprain), "फिसल" (slipped), "टक्कर" (collision), "शीशा टूट" (glass broke), "toot gaya".

**3. English clause scope (from my T2 measurement, dev).** Clauses do not split on commas or "and", so:
- "no power since 9 PM, and the UPS keeps beeping" is one negated clause;
- the self-correction "caught fire at ten, no wait, half past ten" reads as negated because of "no".
This only cost my equipment chips (they skip negated clauses). I am telling you in case it matters for your rules. No action needed from me.
