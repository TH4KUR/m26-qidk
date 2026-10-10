#!/usr/bin/env python3
"""Evaluate candidate YamNet mapper rules on the stored per-clip scores from probe_yamnet.py (no model needed).

    ~/.venvs/megathon-signals/bin/python -I analyze_candidates.py CLASS_MAP.csv OUT_DIR/yamnet_per_clip.json [onnx|tflite]

A candidate is (names, threshold, suppress_names, suppress_threshold): it fires on a clip when the best score
over `names` (max over windows) is >= threshold and no `suppress_names` class reaches suppress_threshold.
Prints, per candidate, hits on its target categories (true positives) and on every other category (false
positives, where 'tolerated' categories are listed apart). Few clips per category (5): read the counts as
a sanity check, not as tuned thresholds.
"""
import csv
import json
import sys
from collections import OrderedDict, defaultdict

NAMES_FIRE = ["Fire alarm", "Smoke detector, smoke alarm"]
NAMES_SIREN = ["Siren", "Civil defense siren", "Emergency vehicle", "Ambulance (siren)", "Police car (siren)",
               "Fire engine, fire truck (siren)"]
RAIN = ["Rain", "Raindrop", "Rain on surface"]
# name: (names, threshold, suppress_names, suppress_threshold, target categories, tolerated categories)
CANDIDATES = OrderedDict([
    ("fire alarm  current .30", (NAMES_FIRE, .30, [], 0, {"clock_alarm"}, set())),
    ("fire alarm  .20", (NAMES_FIRE, .20, [], 0, {"clock_alarm"}, set())),
    ("fire alarm  .15", (NAMES_FIRE, .15, [], 0, {"clock_alarm"}, set())),
    ("alarm(generic) Alarm>=.40", (["Alarm"], .40, [], 0, {"clock_alarm"}, {"siren"})),
    ("alarm(generic) Alarm>=.50", (["Alarm"], .50, [], 0, {"clock_alarm"}, {"siren"})),
    ("alarm(generic) Alarm>=.60", (["Alarm"], .60, [], 0, {"clock_alarm"}, {"siren"})),
    ("alarm(generic) Alarm|Alarm clock|Buzzer|Beep>=.50",
     (["Alarm", "Alarm clock", "Buzzer", "Beep, bleep", "Fire alarm", "Smoke detector, smoke alarm"], .50, [], 0,
      {"clock_alarm"}, {"siren"})),
    ("siren current .30", (NAMES_SIREN, .30, [], 0, {"siren"}, set())),
    ("siren current .50", (NAMES_SIREN, .50, [], 0, {"siren"}, set())),
    ("siren w/o Emergency vehicle .30", ([n for n in NAMES_SIREN if n != "Emergency vehicle"], .30, [], 0, {"siren"}, set())),
    ("siren w/o Emergency vehicle .50", ([n for n in NAMES_SIREN if n != "Emergency vehicle"], .50, [], 0, {"siren"}, set())),
    ("crying current .40", (["Crying, sobbing", "Baby cry, infant cry"], .40, [], 0, {"crying_baby"}, set())),
    ("crying .30", (["Crying, sobbing", "Baby cry, infant cry"], .30, [], 0, {"crying_baby"}, set())),
    ("crying .25", (["Crying, sobbing", "Baby cry, infant cry"], .25, [], 0, {"crying_baby"}, set())),
    ("glass current Shatter>=.30", (["Shatter"], .30, [], 0, {"glass_breaking"}, set())),
    ("glass Shatter>=.15", (["Shatter"], .15, [], 0, {"glass_breaking"}, set())),
    ("glass Shatter|Breaking>=.30", (["Shatter", "Breaking"], .30, [], 0, {"glass_breaking"}, set())),
    ("glass Shatter|Breaking>=.50", (["Shatter", "Breaking"], .50, [], 0, {"glass_breaking"}, set())),
    ("water current .60", (["Water", "Pour", "Trickle, dribble", "Gush", "Drip"], .60, [], 0, {"pouring_water"},
                           {"toilet_flush", "water_drops"})),
    ("water current .60, suppress if Rain*>=.30",
     (["Water", "Pour", "Trickle, dribble", "Gush", "Drip"], .60, RAIN, .30, {"pouring_water"}, {"toilet_flush", "water_drops"})),
    ("water w/o 'Water' .50", (["Pour", "Trickle, dribble", "Gush", "Drip", "Water tap, faucet", "Sink (filling or washing)"], .50, [], 0,
                               {"pouring_water"}, {"toilet_flush", "water_drops"})),
    ("water w/o 'Water' .60", (["Pour", "Trickle, dribble", "Gush", "Drip", "Water tap, faucet", "Sink (filling or washing)"], .60, [], 0,
                               {"pouring_water"}, {"toilet_flush", "water_drops"})),
])


def main() -> None:
    class_map, per_clip_path = sys.argv[1], sys.argv[2]
    backend = sys.argv[3] if len(sys.argv) > 3 else "onnx"
    names = [r["display_name"] for r in csv.DictReader(open(class_map))]
    idx = {n.lower(): i for i, n in enumerate(names)}
    clips = json.load(open(per_clip_path))
    cats = defaultdict(int)
    for c in clips:
        cats[c["category"]] += 1
    print(f"backend={backend}, {len(clips)} clips, categories: {dict(cats)}")
    for label, (cn, thr, sup, sthr, targets, tolerated) in CANDIDATES.items():
        hits = defaultdict(int)
        for c in clips:
            mx = c[backend]["max"]
            best = max(mx[idx[n.lower()]] for n in cn)
            if best >= thr and not any(mx[idx[s.lower()]] >= sthr for s in sup):
                hits[c["category"]] += 1
        tp = {k: f"{hits[k]}/{cats[k]}" for k in targets}
        tol = {k: f"{v}/{cats[k]}" for k, v in hits.items() if k in tolerated}
        fp = {k: f"{v}/{cats[k]}" for k, v in hits.items() if k not in targets and k not in tolerated}
        print(f"{label:<52} TP {tp}  tolerated {tol or '-'}  FP {fp or '-'}")


if __name__ == "__main__":
    main()
