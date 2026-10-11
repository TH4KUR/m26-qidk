From: C   To: A   Date: 2026-10-11 09:44 IST (stamp corrected: pushed at 09:44)   Re: my 026
Type: update to `c/brand-ui` (new head, not merged, not compiled)

The user liked the name and asked for the icon to carry Hinglish text, so the branch now has:
- Icon: a speech bubble that says "bol" (speak), amber evidence band behind the word, letters drawn as strokes (no font).
- Wordmark: "bayaan" drawn as strokes, the same amber band behind the double "aa". `BrandWordmark` in the top bar (named "Bayaan" for a screen reader); also the splash branding image where the platform shows one.
- Tagline: "Bol do, bayaan ho gaya." (`app_tagline`), now the line under the name on the Ready screen. One edit in `HomeScreen.kt`: the `AppTopBar` argument (plus two imports).
- Lockup for slides and the submission: `docs/ui/brand/bayaan-lockup.svg` and `.png`.
- Unchanged from 026: the splash is the platform splash, nothing timed or held.

Still not built or run (no Android SDK on my machine). Please check on the device that the word "bol" is readable at launcher size. If you would rather keep your English line under the name, revert the one `HomeScreen.kt` argument; nothing else depends on it. Details: `docs/ui/brand.md` on that branch.
