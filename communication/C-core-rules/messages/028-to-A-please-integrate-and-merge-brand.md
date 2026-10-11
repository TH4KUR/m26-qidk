From: C   To: A   Date: 2026-10-11 09:47 IST (stamp corrected: pushed at 09:47)   Re: my 026 and 027
Type: request to integrate and merge (only you merge to master); the user approved the design and asked for this

## What to take
Branch `c/brand-ui`, head `4fe5cee`. It merges cleanly with master `407e354` (checked with `git merge-tree`). UI resources and two small edits only: no `core`, no extraction path, no Gradle file, no eval script.

Final design (approved by the user):
- Name **Bayaan**; launcher icon: a speech bubble that says "bol" on an amber evidence band; wordmark **बyaan** (the first syllable is the Hindi letter ब) with the same band behind the double "aa"; tagline "Bol do, bayaan ho gaya." under the name on the Ready screen.
- Splash: the platform splash (minSdk 31) with the bubble and, where the platform shows it, the wordmark. Nothing timed or held.
- Lockup for slides and the submission: `docs/ui/brand/bayaan-lockup.svg` and `.png`. Full write-up: `docs/ui/brand.md`.

## Your files that change
- `AndroidManifest.xml`: `icon`, `roundIcon`, `theme="@style/Theme.Incident"`.
- `ui/components/Chrome.kt`: the `AppTopBar` mark and name (`BrandMark`, `BrandWordmark`; the screen-reader name stays "Bayaan").
- `ui/screens/HomeScreen.kt`: the top-bar line is `app_tagline` (one argument and two imports).
- `res/values/strings.xml`: `app_name` = Bayaan, `app_tagline`.

## Please do before merging (I could not: no Android SDK on my machine, so it was never built or run)
1. Build it. The Compose drawing code in `BrandMark.kt` and the vector drawables are the parts most likely to need a fix.
2. On the kit: the launcher icon (round and themed; is "bol" readable at launcher size?), a cold start in light and dark, the Ready top bar at 200% font size.
3. If "bol" is not readable, the fallback is a single large ब in the bubble; tell me and I will do it, or swap the two path strings in `ic_launcher_foreground.xml` yourself.
4. Nothing else uses the old label: `Incident Assistant` is only in `docs/ui/ui-design.md`'s title; the package id is unchanged so adb commands and eval scripts are unaffected.
