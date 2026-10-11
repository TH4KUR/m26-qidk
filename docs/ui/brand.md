# Brand: name, icon, splash

Owner of the change: C (proposal on `c/brand-ui`, built on master `a77d42f`). The app code and the screens stay A's.

## Name: Bayaan

*Bayaan* (बयान) is Hindi and Urdu for a statement: what a person says happened. That is the job of the app: the worker gives
their statement by voice, and the report is written from their own words, each field with the words that support it.

- It says what the product does, in one word, and it works for the Hindi and Telugu lanes later without a rename.
- Short enough for a launcher label (6 letters), easy to say, easy to type.
- It does not use the sponsor's name or marks, nor any team, member or college name (double-blind).
- Spelled with a double "a" so that English readers say it the way it is said (bah-YAAN), not "BAY-an".
- Package id stays `com.megathon.incident` (the `adb` commands, eval scripts and docs use it).

Alternatives considered: **Noted** (friendly English, but generic), **Aloud** (says "speak", not "report").

Tagline, already in the app: "Your voice. A clear report."

## Icon

A speech bubble holding report lines: what was said becomes the report. An amber band sits behind the second line, the
same evidence highlight the Report screen draws behind the words that support a field.

| Part | Value |
|---|---|
| tile (adaptive background) | `#0B6E66`, the accent |
| bubble | `#F4FBFA` |
| report lines | `#0B6E66`, 4 / 108 stroke, round caps |
| evidence band | `#F2BE72` (the amber of the "suggested" status) |
| themed icon (Android 13) | one shape, lines cut out (`ic_launcher_monochrome.xml`) |

Solid colours only: no gradient, no glow, red is never used (design brief section 4). The art sits inside the 66 dp safe zone of
the 108 dp adaptive icon, so round, squircle and teardrop masks all keep it whole.

## Splash

The platform splash (minSdk is 31, so it is always there), configured in `res/values/themes.xml` and `values-night`:
canvas colour behind, the bubble in the accent in front, mint on the dark canvas at night. It leaves on the first drawn frame.

Deliberately **not** a timed in-app splash with the name on it:

- The brief says nothing auto-advances, and a worker reporting an incident should not wait for a logo.
- A's Ready screen already starts the mic while the model warms up ("You can start while it warms up").
- The name is on the first frame the app draws: the top bar of the Ready screen shows the mark and "Bayaan".

If a held, named splash is still wanted for the demo, it is one composable in front of `HomeScreen` shown while
`ui.readiness == Readiness.LOADING`; it should be dismissable by a tap.

## What changed

| File | Change |
|---|---|
| `res/values/strings.xml` | `app_name` = Bayaan |
| `res/values/colors.xml`, `values-night/colors.xml` | brand and splash colours (same hex values as the palette) |
| `res/values/themes.xml`, `values-night/themes.xml` | `Theme.Incident`: splash background and icon, window background |
| `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `splash_icon.xml` | the mark |
| `res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | adaptive icon with monochrome layer |
| `AndroidManifest.xml` | `icon`, `roundIcon`, `theme="@style/Theme.Incident"` |
| `ui/components/BrandMark.kt` (new) | the same mark drawn in Compose |
| `ui/components/Chrome.kt` | top bar uses `BrandMark` and the app name instead of the three bars and the word "incident" |

## Not verified

There is no Android SDK on the machine this was written on, so nothing here was compiled or run on a device. The XML files parse;
the vector paths were previewed as SVG. Before it goes to master: build the app, look at the icon on the launcher (round and
themed), the splash on a cold start in light and dark, and the top bar at 200% font size.
