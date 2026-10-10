#!/usr/bin/env python3
"""YamNet probe: (1) check the AudioSet names in YamNetSignalMapper.kt against the class map,
(2) run YamNet on ESC-50 clips and report which AudioSet classes fire and whether the mapper rules would fire.

Run with the venv python and -I (isolated mode), because it reads downloaded files:

    ~/.venvs/megathon-signals/bin/python -I probe_yamnet.py \
        --class-map yamnet_class_map.csv --mapper-kt core/src/main/kotlin/.../YamNetSignalMapper.kt \
        --check-only                                   # step 1 only, no model needed
    ~/.venvs/megathon-signals/bin/python -I probe_yamnet.py \
        --class-map yamnet_class_map.csv --mapper-kt .../YamNetSignalMapper.kt \
        --onnx MODELS/yamnet.onnx --tflite MODELS/yamnet_google_mediapipe.tflite \
        --clips ESC50_DIR --out OUT_DIR [--top 5]

Backends: --onnx is the Qualcomm AI Hub export (input = one 96x64 log-mel patch, so the mel front end is
done here in numpy exactly like YAMNet's; its output is raw logits and the sigmoid is applied here); --tflite is Google's yamnet.tflite (input = 15600 raw samples,
front end inside the model). They should agree; the first is what the Android app will most likely run.
Aggregation over time: per class, the MAX over patches (0.96 s windows, 0.48 s hop). The mean is also stored.
"""
import argparse
import collections
import csv
import json
import re
import statistics
from math import gcd
from pathlib import Path

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

SR = 16000
# Which mapper label we would call "the intended hazard" for each ESC-50 category. Empty set = no hazard intended.
# clock_alarm is only a proxy for a smoke detector beep (ESC-50 has no smoke alarm category).
EXPECTED = {
    "siren": {"siren"}, "clock_alarm": {"fire alarm"}, "crying_baby": {"crying"},
    "glass_breaking": {"glass breaking"}, "crackling_fire": set(), "coughing": set(), "sneezing": set(),
    "door_wood_knock": set(), "vacuum_cleaner": set(), "water_drops": set(), "pouring_water": {"running water"},
    "rain": set(), "toilet_flush": set(), "church_bells": set(), "car_horn": set(),
    "smoke_alarm_commons": {"fire alarm"},  # optional extra: a real smoke alarm recording (see README)
}
# Labels that are not a mistake even though no hazard is intended (it really is water): shown separately.
TOLERATED = {"water_drops": {"running water"}, "toilet_flush": {"running water"}}


# ------------------------------------------------------------------ class map and mapper rules
def load_class_map(path: Path) -> list[str]:
    with path.open(newline="") as f:
        rows = list(csv.DictReader(f))
    names = [r["display_name"] for r in rows]
    assert [int(r["index"]) for r in rows] == list(range(len(rows))), "class map index gap"
    return names


def _rule_bodies(text: str) -> list[str]:
    """Argument text of every `Rule(...)` call, matching parentheses outside of string literals
    (AudioSet names such as "ambulance (siren)" contain parentheses)."""
    bodies = []
    for m in re.finditer(r"\bRule\(", text):
        depth, i, in_str = 1, m.end(), False
        while i < len(text) and depth:
            ch = text[i]
            if ch == '"':
                in_str = not in_str
            elif not in_str:
                depth += (ch == "(") - (ch == ")")
            i += 1
        bodies.append(text[m.end():i - 1])
    return bodies


def parse_mapper(kt: Path) -> list[dict]:
    """Extract Rule("label", setOf("a", "b"), 0.30f) entries from YamNetSignalMapper.kt (comments stripped)."""
    text = re.sub(r"^\s*//[^\n]*", "", kt.read_text(), flags=re.M)
    text = re.sub(r"//[^\n\"]*$", "", text, flags=re.M)
    rules = []
    for body in _rule_bodies(text):
        if "class Rule" in body or "val " in body:  # the class declaration, not a rule
            continue
        strings = re.findall(r'"([^"]*)"', body)
        score = re.search(r"([0-9.]+)f\s*,?\s*$", body.strip())
        assert strings and score, "cannot parse rule: " + body
        rules.append({"label": strings[0], "names": strings[1:], "min": float(score.group(1))})
    assert rules, "no rules parsed from " + str(kt)
    return rules


def check_names(rules: list[dict], class_map: list[str]) -> list[str]:
    import difflib
    lower = {n.lower(): n for n in class_map}
    lines, bad = [], 0
    for r in rules:
        for n in r["names"]:
            if n.lower() in lower:
                lines.append(f"  OK       {r['label']:<15} {n!r} -> index {class_map.index(lower[n.lower()])}")
            else:
                bad += 1
                close = difflib.get_close_matches(n.lower(), list(lower), n=4, cutoff=0.4)
                words = [w for w in re.findall(r"[a-z]+", n.lower()) if len(w) > 3]
                close += [k for k in lower if any(w in k for w in words) and k not in close][:6]
                lines.append(f"  MISSING  {r['label']:<15} {n!r}; closest real names: {[lower[c] for c in close[:8]]}")
    lines.append(f"{bad} of {sum(len(r['names']) for r in rules)} mapper names are NOT in the class map")
    return lines


# ------------------------------------------------------------------ audio and front end
def load_16k_mono(path: Path) -> np.ndarray:
    x, sr = sf.read(str(path), dtype="float32", always_2d=True)
    x = x.mean(axis=1)
    if sr != SR:
        g = gcd(sr, SR)
        x = resample_poly(x, SR // g, sr // g).astype(np.float32)
    return np.ascontiguousarray(x, dtype=np.float32)


def _hz_to_mel(f):
    return 1127.0 * np.log(1.0 + f / 700.0)


def mel_matrix(n_mel=64, n_bins=257, sr=SR, lo=125.0, hi=7500.0) -> np.ndarray:
    """YAMNet's mel_features.spectrogram_to_mel_matrix (HTK mel, DC bin zeroed)."""
    bins_mel = _hz_to_mel(np.linspace(0.0, sr / 2.0, n_bins))
    edges = np.linspace(_hz_to_mel(lo), _hz_to_mel(hi), n_mel + 2)
    w = np.zeros((n_bins, n_mel))
    for i in range(n_mel):
        l, c, u = edges[i:i + 3]
        w[:, i] = np.maximum(0.0, np.minimum((bins_mel - l) / (c - l), (u - bins_mel) / (u - c)))
    w[0, :] = 0.0
    return w


def log_mel(x: np.ndarray) -> np.ndarray:
    """(frames, 64) log-mel like YAMNet: 25 ms periodic Hann window, 10 ms hop, 512-point |FFT| (magnitude),
    64 mel bins 125-7500 Hz, log(mel + 0.001). No padding at the end."""
    win, hop, nfft = 400, 160, 512
    n = 1 + (len(x) - win) // hop
    if n < 1:
        x = np.pad(x, (0, win - len(x)))
        n = 1
    idx = np.arange(win)[None, :] + hop * np.arange(n)[:, None]
    window = 0.5 - 0.5 * np.cos(2 * np.pi * np.arange(win) / win)
    spec = np.abs(np.fft.rfft(x[idx] * window, nfft, axis=1))
    return np.log(spec @ mel_matrix() + 0.001).astype(np.float32)


class OnnxYamnet:
    def __init__(self, path: Path):
        import onnxruntime as ort
        so = ort.SessionOptions()
        so.intra_op_num_threads = 2
        self.s = ort.InferenceSession(str(path), so, providers=["CPUExecutionProvider"])

    def scores(self, x: np.ndarray) -> np.ndarray:
        lm = log_mel(x)
        starts = list(range(0, max(1, len(lm) - 96 + 1), 48))
        patches = [lm[s:s + 96] for s in starts if len(lm[s:s + 96]) == 96]
        if not patches:  # clip shorter than 0.96 s: zero-pad to one patch
            patches = [np.pad(lm, ((0, 96 - len(lm)), (0, 0)), constant_values=np.log(0.001))]
        raw = np.stack([self.s.run(None, {"audio": p[None, None]})[0][0] for p in patches]).astype(np.float64)
        self.raw_min, self.raw_max = float(raw.min()), float(raw.max())
        # The AI Hub export returns pre-sigmoid LOGITS (seen here: about -88 .. +73), not 0..1 scores.
        # Our mapper thresholds are probabilities, so apply the sigmoid per patch BEFORE aggregating.
        return 1.0 / (1.0 + np.exp(-raw))  # (patches, 521)


class TfliteYamnet:
    def __init__(self, path: Path):
        from ai_edge_litert.interpreter import Interpreter
        self.i = Interpreter(model_path=str(path), num_threads=2)
        self.i.allocate_tensors()
        self.inp = self.i.get_input_details()[0]
        self.out = self.i.get_output_details()[0]

    def scores(self, x: np.ndarray) -> np.ndarray:
        n = 15600
        starts = list(range(0, max(1, len(x) - n + 1), 7680))
        out = []
        for s in starts:
            w = x[s:s + n]
            if len(w) < n:
                w = np.pad(w, (0, n - len(w)))
            self.i.set_tensor(self.inp["index"], w)
            self.i.invoke()
            out.append(self.i.get_tensor(self.out["index"])[0].copy())
        return np.stack(out)


# ------------------------------------------------------------------ mapper emulation and summaries
def rule_hits(rules: list[dict], score_by_name: dict[str, float]) -> dict[str, float]:
    """Same logic as YamNetSignalMapper.map: best score per rule over its names, kept if >= minScore."""
    hits = {}
    for r in rules:
        best = max((score_by_name.get(n.lower(), 0.0) for n in r["names"]), default=0.0)
        if best >= r["min"]:
            hits[r["label"]] = best
    return hits


def rule_best(rules: list[dict], score_by_name: dict[str, float]) -> dict[str, float]:
    return {r["label"]: max((score_by_name.get(n.lower(), 0.0) for n in r["names"]), default=0.0) for r in rules}


def q(v):
    return float(np.round(v, 3))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--class-map", type=Path, required=True)
    ap.add_argument("--mapper-kt", type=Path, required=True)
    ap.add_argument("--check-only", action="store_true")
    ap.add_argument("--onnx", type=Path)
    ap.add_argument("--tflite", type=Path)
    ap.add_argument("--clips", type=Path, help="ESC-50 style dir of wavs plus esc50.csv inside it")
    ap.add_argument("--meta", type=Path, help="esc50.csv (default: CLIPS/esc50.csv)")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--top", type=int, default=5)
    a = ap.parse_args()

    names = load_class_map(a.class_map)
    print(f"class map: {len(names)} classes, first={names[0]!r}, last={names[-1]!r}")
    rules = parse_mapper(a.mapper_kt)
    print("\nMapper name check (case-insensitive) against the class map")
    print("\n".join(check_names(rules, names)))
    if a.check_only:
        return

    backends = {}
    if a.onnx:
        backends["onnx"] = OnnxYamnet(a.onnx)
    if a.tflite:
        backends["tflite"] = TfliteYamnet(a.tflite)
    assert backends and a.clips and a.out, "need --onnx and/or --tflite, --clips and --out"
    a.out.mkdir(parents=True, exist_ok=True)
    meta = list(csv.DictReader((a.meta or a.clips / "esc50.csv").open()))
    cat_of = {r["filename"]: r["category"] for r in meta}
    files = sorted(p for p in a.clips.glob("*.wav") if p.name in cat_of)
    primary = "onnx" if "onnx" in backends else "tflite"

    per_clip = []  # one record per clip and backend
    for p in files:
        x = load_16k_mono(p)
        rec = {"file": p.name, "category": cat_of[p.name], "seconds": round(len(x) / SR, 2)}
        for bname, b in backends.items():
            sc = b.scores(x)                      # (windows, 521)
            mx, mean = sc.max(axis=0), sc.mean(axis=0)
            order = np.argsort(-mx)[:a.top]
            by_name = {names[i].lower(): float(mx[i]) for i in range(len(names))}
            rec[bname] = {
                "top": [(names[i], q(mx[i])) for i in order],
                "max": mx.tolist(), "mean": mean.tolist(),
                "rule_best": {k: q(v) for k, v in rule_best(rules, by_name).items()},
                "hits": {k: q(v) for k, v in rule_hits(rules, by_name).items()},
                "windows": int(sc.shape[0]),
                **({"raw_logit_min": round(b.raw_min, 2), "raw_logit_max": round(b.raw_max, 2)} if bname == "onnx" else {}),
            }
        per_clip.append(rec)
        print(f"\n{p.name} [{rec['category']}] ({rec['seconds']} s)")
        for bname in backends:
            print(f"  {bname:6} top{a.top} (max over windows): " +
                  "; ".join(f"{n} {s:.2f}" for n, s in rec[bname]["top"]) +
                  f"  | mapper hits: {rec[bname]['hits'] or 'none'}")

    if "onnx" in backends:
        print(f"\nONNX raw output range over all clips (before sigmoid): "
              f"{min(r['onnx']['raw_logit_min'] for r in per_clip)} .. {max(r['onnx']['raw_logit_max'] for r in per_clip)}")
    # per-backend agreement
    if len(backends) == 2:
        top1 = sum(r["onnx"]["top"][0][0] == r["tflite"]["top"][0][0] for r in per_clip)
        diff = [np.abs(np.array(r["onnx"]["max"]) - np.array(r["tflite"]["max"])).max() for r in per_clip]
        same5 = sum(len({n for n, _ in r["onnx"]["top"]} & {n for n, _ in r["tflite"]["top"]}) >= 3 for r in per_clip)
        print(f"\nBackend agreement (onnx after sigmoid vs tflite): same top-1 class on {top1}/{len(per_clip)} clips; "
              f">=3 of top-{a.top} shared on {same5}/{len(per_clip)}; "
              f"max abs score difference per clip: median {statistics.median(diff):.3f}, max {max(diff):.3f}")

    cats = list(dict.fromkeys(r["category"] for r in per_clip))
    summary = {}
    for bname in backends:
        print(f"\n===== PER-CATEGORY SUMMARY ({bname}; aggregation = max over windows per class; "
              f"stat = min/median/max over clips of that per-clip max score) =====")
        for cat in cats:
            rs = [r for r in per_clip if r["category"] == cat]
            mx = np.array([r[bname]["max"] for r in rs])            # (clips, 521)
            med = np.median(mx, axis=0)
            fired = [i for i in np.argsort(-med)[:8] if med[i] >= 0.05]
            # classes that appeared in the top-5 of any clip but were not strong on median
            appear = collections.Counter(n for r in rs for n, _ in r[bname]["top"])
            print(f"\n[{cat}] n={len(rs)} clips")
            for i in fired:
                print(f"    {names[i]!r}: min {mx[:, i].min():.2f} / median {med[i]:.2f} / max {mx[:, i].max():.2f} "
                      f"(in top-{a.top} of {appear.get(names[i], 0)}/{len(rs)} clips)")
            hitcount = collections.Counter(h for r in rs for h in r[bname]["hits"])
            exp, tol = EXPECTED.get(cat, set()), TOLERATED.get(cat, set())
            n_exp = sum(any(h in exp for h in r[bname]["hits"]) for r in rs) if exp else None
            fp = {h: c for h, c in hitcount.items() if h not in exp and h not in tol}
            tolc = {h: c for h, c in hitcount.items() if h in tol}
            print(f"    mapper hits (clips firing each label, of {len(rs)}): {dict(hitcount) or 'none'}")
            if exp:
                print(f"    intended {sorted(exp)} fired on {n_exp}/{len(rs)} clips")
            if fp:
                print(f"    UNINTENDED labels: {fp}")
            if tolc:
                print(f"    tolerated (really water): {tolc}")
            # rule-level best scores (what each rule would see) per category
            rb = {r["label"]: [x["rule_best"][r["label"]] for x in [y[bname] for y in rs]] for r in rules}
            print("    rule best-score min/median/max: " + "; ".join(
                f"{k} {min(v):.2f}/{statistics.median(v):.2f}/{max(v):.2f}" for k, v in rb.items()))
            summary.setdefault(bname, {})[cat] = {
                "n": len(rs),
                "classes": {names[i]: [q(mx[:, i].min()), q(med[i]), q(mx[:, i].max())] for i in fired},
                "hits": dict(hitcount), "unintended": fp,
                "rule_best": {k: [min(v), q(statistics.median(v)), max(v)] for k, v in rb.items()},
            }

    (a.out / "yamnet_per_clip.json").write_text(json.dumps(per_clip))
    (a.out / "yamnet_summary.json").write_text(json.dumps(summary, indent=1))
    print("\nwrote", a.out / "yamnet_per_clip.json", "and yamnet_summary.json")


if __name__ == "__main__":
    main()
