#!/usr/bin/env python3
"""WCAG 2.1 contrast check for the Bayaan palettes.

Parses the hex values of the IncidentColors palettes (LightColors, DarkColors, HindiLightColors, HindiDarkColors) straight out of
app/src/main/kotlin/com/megathon/incident/ui/theme/Tokens.kt, computes the contrast ratio of every text-on-surface pair, button-label
pair and control outline the app uses, and exits 1 if any pair is below its AA threshold (4.5:1 text, 3:1 large text and UI components).

It also checks that, in the Hindi palettes, the danger and simulated colours stay clearly apart from the burgundy accent
(CIELAB distance and hue angle), because danger must never read as "the brand".

Run:  python3 tools/theme_contrast_check.py            (table on stdout, exit status 0/1)
      python3 tools/theme_contrast_check.py --markdown (the tables as used in docs/ui/theme-burgundy.md)
The English palettes are checked for information only: they are frozen, and their failures (if any) do not change the exit status
unless --strict-english is given.
"""
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOKENS = ROOT / "app/src/main/kotlin/com/megathon/incident/ui/theme/Tokens.kt"
BRANDMARK = ROOT / "app/src/main/kotlin/com/megathon/incident/ui/components/BrandMark.kt"

PALETTES = ["LightColors", "DarkColors", "HindiLightColors", "HindiDarkColors"]
ROLES = ["canvas", "surface", "ink", "ink2", "ink3", "line", "accent", "onAccent", "accentContainer", "onAccentContainer",
         "attentionContainer", "onAttention", "dangerContainer", "onDanger", "danger", "simulatedContainer", "onSimulated",
         "simulated", "infoContainer", "onInfo"]


def parse_palettes(text):
    out = {}
    for name in PALETTES:
        m = re.search(r"val\s+" + name + r"\s*=\s*IncidentColors\((.*?)\n\)", text, re.S)
        if not m:
            sys.exit("palette %s not found in Tokens.kt" % name)
        vals = dict(re.findall(r"(\w+)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", m.group(1)))
        vals = {k: v.upper() for k, v in vals.items()}
        missing = [r for r in ROLES if r not in vals]
        if missing:
            sys.exit("palette %s misses roles: %s" % (name, ", ".join(missing)))
        dark = re.search(r"isDark\s*=\s*(true|false)", m.group(1))
        vals["isDark"] = dark.group(1) == "true"
        out[name] = vals
    return out


def rgb(h):
    return tuple(int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))


def lin(c):
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4


def luminance(h):
    r, g, b = map(lin, rgb(h))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(a, b):
    hi, lo = sorted((luminance(a), luminance(b)), reverse=True)
    return (hi + 0.05) / (lo + 0.05)


def blend(fg, bg, alpha):
    f, b = rgb(fg), rgb(bg)
    return "".join("%02X" % round((alpha * x + (1 - alpha) * y) * 255) for x, y in zip(f, b))


def lab(h):
    r, g, b = map(lin, rgb(h))
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    return 116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))


def delta_e(a, b):
    return math.dist(lab(a), lab(b))


def hue_angle(h):
    _, a, b = lab(h)
    return math.degrees(math.atan2(b, a)) % 360


def hue_gap(a, b):
    d = abs(hue_angle(a) - hue_angle(b)) % 360
    return min(d, 360 - d)


def pairs(p):
    """(label, use, foreground, background, required ratio) for one palette."""
    dark = p["isDark"]
    chip = lambda base: blend(p["line"], p[base], 0.55)  # the "None" pill: line at 55% over the surface it sits on
    on_danger = p["canvas"] if dark else p["onAccent"]    # the mic button's glyph while listening (MicButton.kt)
    on_error = p["canvas"] if dark else p["surface"]      # Material onError (Theme.kt)
    T, U = 4.5, 3.0
    return [
        ("ink on canvas", "body text", p["ink"], p["canvas"], T),
        ("ink on surface", "card text", p["ink"], p["surface"], T),
        ("ink2 on canvas", "secondary text", p["ink2"], p["canvas"], T),
        ("ink2 on surface", "secondary text, icons", p["ink2"], p["surface"], T),
        ("ink3 on canvas", "caption text", p["ink3"], p["canvas"], T),
        ("ink3 on surface", "caption text", p["ink3"], p["surface"], T),
        ("ink on None pill (over canvas)", "chip label", p["ink"], chip("canvas"), T),
        ("ink on None pill (over surface)", "chip label", p["ink"], chip("surface"), T),
        ("accent on canvas", "accent text, links", p["accent"], p["canvas"], T),
        ("accent on surface", "accent text, links", p["accent"], p["surface"], T),
        ("onAccent on accent", "primary button label, Confirmed pill", p["onAccent"], p["accent"], T),
        ("onAccentContainer on accentContainer", "Heard pill, chips", p["onAccentContainer"], p["accentContainer"], T),
        ("onAttention on attentionContainer", "Suggested pill", p["onAttention"], p["attentionContainer"], T),
        ("onDanger on dangerContainer", "Needed pill, errors", p["onDanger"], p["dangerContainer"], T),
        ("danger on canvas", "status text while recording", p["danger"], p["canvas"], T),
        ("danger on surface", "danger text and icons", p["danger"], p["surface"], T),
        ("onSimulated on simulatedContainer", "SIMULATED strip", p["onSimulated"], p["simulatedContainer"], T),
        ("onInfo on infoContainer", "Not sure pill", p["onInfo"], p["infoContainer"], T),
        ("mic glyph on danger", "mic button while listening", on_danger, p["danger"], T),
        ("onError on danger", "Material error button label", on_error, p["danger"], T),
        ("ink3 outline on canvas", "control outline (3:1)", p["ink3"], p["canvas"], U),
        ("ink3 outline on surface", "control outline (3:1)", p["ink3"], p["surface"], U),
        ("accent on canvas", "focus ring, progress, slider (3:1)", p["accent"], p["canvas"], U),
        ("accent on surface", "focus ring, progress, slider (3:1)", p["accent"], p["surface"], U),
        ("accent on accentContainer", "icon on tinted fill (3:1)", p["accent"], p["accentContainer"], U),
        ("danger on dangerContainer", "icon on tinted fill (3:1)", p["danger"], p["dangerContainer"], U),
        ("simulated on canvas", "solid simulated fill (3:1)", p["simulated"], p["canvas"], U),
        ("simulated on surface", "solid simulated fill (3:1)", p["simulated"], p["surface"], U),
    ]


def main():
    markdown = "--markdown" in sys.argv
    strict_english = "--strict-english" in sys.argv
    palettes = parse_palettes(TOKENS.read_text())
    brand = BRANDMARK.read_text()
    highlight = re.search(r"HIGHLIGHT\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", brand).group(1).upper()
    letters = re.search(r"LETTERS\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", brand).group(1).upper()
    failures = []
    english_failures = []

    for name in PALETTES:
        p = palettes[name]
        hindi = name.startswith("Hindi")
        if markdown:
            print("\n### %s\n\n| Pair | Use | Foreground | Background | Ratio | Needs | |\n|---|---|---|---|---|---|---|" % name)
        else:
            print("\n== %s ==" % name)
        for label, use, fg, bg, need in pairs(p):
            r = ratio(fg, bg)
            ok = r >= need
            if not ok:
                (failures if hindi or strict_english else english_failures).append("%s: %s (%s on %s) %.2f < %.1f" % (name, label, fg, bg, r, need))
            if markdown:
                print("| %s | %s | `#%s` | `#%s` | %.2f | %.1f | %s |" % (label, use, fg, bg, r, need, "pass" if ok else "**FAIL**"))
            else:
                print("  %-4s %5.2f (needs %.1f)  %-38s %s on %s  [%s]" % ("ok" if ok else "FAIL", r, need, label, fg, bg, use))

    print("\n== Brand marks (fixed colours, same in every palette) ==" if not markdown else "\n### Brand marks (fixed colours, same in every palette)\n")
    r = ratio(letters, highlight)
    line = "wordmark and mark letters %s on the amber band %s: %.2f (needs 4.5)" % (letters, highlight, r)
    print(("- " if markdown else "  ") + line)
    if r < 4.5:
        failures.append("brand: " + line)
    for name in PALETTES:
        p = palettes[name]
        print(("- " if markdown else "  ") + "%s: amber band %s against the bubble (onAccent %s): %.2f (decorative highlight, the letters carry the meaning)"
              % (name, highlight, p["onAccent"], ratio(highlight, p["onAccent"])))

    print("\n== Distinguishability (accent vs danger vs simulated) ==" if not markdown else "\n### Distinguishability\n\n| Palette | Pair | CIELAB distance | Hue gap | Lightness gap (L*) | |\n|---|---|---|---|---|---|")
    for name in PALETTES:
        p = palettes[name]
        hindi = name.startswith("Hindi")
        for a, b in (("accent", "danger"), ("accent", "simulated"), ("danger", "simulated")):
            de, hg = delta_e(p[a], p[b]), hue_gap(p[a], p[b])
            dl = abs(lab(p[a])[0] - lab(p[b])[0])
            # Two colours are "clearly apart" when they differ a lot in hue or a lot in lightness, and are far apart overall.
            ok = de >= 35 and (hg >= 30 or dl >= 25)
            if hindi and not ok:
                failures.append("%s: %s vs %s too close (dE %.0f, hue gap %.0f, dL %.0f)" % (name, a, b, de, hg, dl))
            if markdown:
                print("| %s | %s vs %s | %.0f | %.0f deg | %.0f | %s |" % (name, a, b, de, hg, dl, "pass" if ok else ("**FAIL**" if hindi else "n/a (frozen)")))
            else:
                print("  %-4s %-17s %-10s vs %-10s dE %3.0f  hue gap %3.0f deg  dL %3.0f" % ("ok" if ok else ("FAIL" if hindi else "n/a"), name, a, b, de, hg, dl))

    if english_failures:
        print("\nEnglish palette (frozen, informational):")
        for f in english_failures:
            print("  " + f)
    if failures:
        print("\nFAILED:")
        for f in failures:
            print("  " + f)
        return 1
    print("\nAll Hindi palette pairs pass WCAG 2.1 AA.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
