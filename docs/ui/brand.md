# Brand: name, icon, wordmark, tagline, splash

Owner of the change: C (proposal on `c/brand-ui`, built on master `a77d42f`). The app code and the screens stay A's.
Lockup for slides and the submission: `docs/ui/brand/bayaan-lockup.svg` and `.png`.

## Name: Bayaan

*Bayaan* (बयान) is Hindi and Urdu for a statement: what a person says happened. That is the job of the app: the worker gives
their statement by voice, and the report is written from their own words, each field with the words that support it.

- It says what the product does, in one word, and it works for the Hindi and Telugu lanes later without a rename.
- Short enough for a launcher label (6 letters), easy to say, easy to type.
- It does not use the sponsor's name or marks, nor any team, member or college name (double-blind).
- Spelled with a double "a" so that English readers say it the way it is said (bah-YAAN), not "BAY-an".
- Package id stays `com.megathon.incident` (the `adb` commands, eval scripts and docs use it).

## Tagline: "Bol do, bayaan ho gaya."

Hinglish: *bol do* = "say it" (bolna, to speak), *bayaan ho gaya* = "the statement is done". It rhymes with the product: you
speak, and the statement is made. Devanagari: बोल दो, बयान हो गया। Shown under the wordmark on the Ready screen
(`app_tagline` in `strings.xml`).

## Mark: a bubble that says "bol"

A speech bubble with the word **bol** (बोल, "speak") in it, and an amber band behind the word: the same evidence highlight the
Report screen draws behind the words that support a field. The letters are strokes (b = stem and bowl, o = ring, l = stem), not
a font, so they look the same on every device.

| Part | Value |
|---|---|
| tile (adaptive background) | `#0B6E66`, the accent |
| bubble | `#F4FBFA` |
| letters | `#0A4F49` (dark enough to read on the amber), 3.6 / 108 stroke, round caps |
| evidence band | `#F2BE72` (the amber of the "suggested" status) |
| themed icon (Android 13) | bubble outline and the word, one colour (`ic_launcher_monochrome.xml`) |

Solid colours only: no gradient, no glow, red is never used (design brief section 4). The art sits inside the 66 dp safe zone of
the 108 dp adaptive icon, so round, squircle and teardrop masks all keep it whole.

## Wordmark: बyaan

The first syllable "ba" is the Hindi letter **ब**, the rest is Latin, so the name is written the way it is spoken in Hinglish.
Drawn as rounded strokes (the same line weight and round caps as the mark), with the amber evidence band behind the double
**aa**: the app's one signature, "show the words that support it", on its own name. The letter ब is traced from the standard
form (headline, stem, bowl, slash), not set in a font. In the app it is `BrandWordmark` in the top bar; on the splash it is
`splash_branding.xml`. The screen reader name stays "Bayaan"; the tagline and docs keep the Latin spelling "bayaan".

## Splash

The platform splash (minSdk is 31, so it is always there), configured in `res/values/themes.xml` and `values-night`:
canvas colour behind, the bubble in the accent in front (mint on the dark canvas), and the wordmark as the branding image where
the platform shows one. It leaves on the first drawn frame.

Deliberately **not** a timed in-app splash:

- The brief says nothing auto-advances, and a worker reporting an incident should not wait for a logo.
- A's Ready screen already starts the mic while the model warms up ("You can start while it warms up").
- The name and the tagline are on the first frame the app draws: the top bar of the Ready screen.

The platform decides whether the branding image is drawn (it has been dropped on some Android versions and skins). If it is
not, the splash is the bubble alone and nothing else changes.

If a held, named splash is still wanted for the demo, it is one composable in front of `HomeScreen` shown while
`ui.readiness == Readiness.LOADING`; it should be dismissable by a tap.

## What changed

| File | Change |
|---|---|
| `res/values/strings.xml` | `app_name` = Bayaan, `app_tagline` |
| `res/values/colors.xml`, `values-night/colors.xml` | brand and splash colours (same hex values as the palette) |
| `res/values/themes.xml`, `values-night/themes.xml` | `Theme.Incident`: splash background, icon and branding image, window background |
| `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `splash_icon.xml`, `splash_branding.xml` | the mark and the wordmark |
| `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon with monochrome layer |
| `AndroidManifest.xml` | `icon`, `roundIcon`, `theme="@style/Theme.Incident"` |
| `ui/components/BrandMark.kt` (new) | the mark (`BrandMark`) and the wordmark (`BrandWordmark`) drawn in Compose |
| `ui/components/Chrome.kt` | top bar: the mark and the wordmark (named "Bayaan" for a screen reader) instead of the three bars and the word "incident" |
| `ui/screens/HomeScreen.kt` | the top bar line under the name is `app_tagline` (was "Your voice. A clear report.") |

## Text size

The top-bar wordmark is sized in sp (20 sp), not dp, so it grows with the system font size and keeps its proportion to the
line under it; the two sit in one column with a 4 dp gap. The kit showed the wordmark overlapping the subtitle on the
"Shape your report" screen at 200% text size (A/041) when the wordmark was a fixed 20 dp; this layout removes the fixed
height. It has not been looked at on the device since the change.

## Not verified

There is no Android SDK on the machine this was written on, so nothing here was compiled or run on a device. The XML files parse;
the lockup was rendered in a browser. Before it goes to master: build the app, look at the icon on the launcher (round and
themed; the word "bol" is small at 48 px, check it is readable on the device), the splash on a cold start in light and dark, and
the top bar at 200% font size.
