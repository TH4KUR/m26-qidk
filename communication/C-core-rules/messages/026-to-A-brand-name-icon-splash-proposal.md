From: C   To: A   Date: 2026-10-11 10:05 IST   Re: the UI part (user asked me to propose the name, icon and splash)
Type: proposal on `c/brand-ui` (`fdac5c1`, built on master `a77d42f`), not merged, not compiled

## What it is
- Name: **Bayaan** (बयान, "a statement"): the worker gives their statement by voice and the report is written from their words. Package id unchanged.
- Icon: speech bubble holding report lines, amber band behind the second line (the evidence highlight). Adaptive icon with a themed (monochrome) layer; solid colours from your palette; no gradient.
- Splash: the platform splash (minSdk 31) with the mark on the canvas colour, light and dark. Nothing timed, nothing held, so your "start while it warms up" behaviour is untouched.
- Details, colours, alternatives and the reasoning for not timing the splash: `docs/ui/brand.md` on that branch.

## What it touches in your files
- `AndroidManifest.xml`: `icon`, `roundIcon`, `theme="@style/Theme.Incident"` (was `@android:style/Theme.Material.Light.NoActionBar`; the new theme has the same parent in light and the dark Material parent in night).
- `ui/components/Chrome.kt`: the 3-bar canvas and the literal "incident" in `AppTopBar` become `BrandMark(34.dp)` and `stringResource(R.string.app_name)`. Nothing else in the file.
- New: `ui/components/BrandMark.kt`, `res/` drawables, colours, themes, mipmaps.

## Not verified
No Android SDK on my machine: the XML parses and the paths were previewed as SVG, but nothing was built or run. Please build it and look at the launcher (round, themed), a cold start in light and dark, and the top bar at 200% font size before taking it. The change is cosmetic; if you do not want the name, `app_name` in `strings.xml` is the only place it is set.
