# Arena synthesis: recall and triage (2026-10-11)
Base: c2 for extraction (only candidate with a working patch, replay reproduces v6 57/135 exactly, verified: applies to master, 794 core tests, 1 expected failure wrongValueTypeFails). c1 for structure, triage and schedule. Judge (opus, saw c1/c3/c4 only): c1 27, c4 25, c3 20. c2 scored by me ~28.
Grafts: c4 severe-under-triage metric + pass rule (under falls, over rises <=5 pp); c3 revert rules (wrong-type-confirmed >1, hallucination >10%), <5 pt = noise; c1 replay table by prompt, gates before kit run.
Rejected: gazetteer equipment extractor now (dev-tuned); cue-mined type evidence unless the cue word was really said; asking impact_hint is cheap but unproven, floors first; any scorer/gold/threshold change; tuning on 13 held-out or spent holdouts 1-3.
Dropouts: none (c2 was interrupted once, resumed, finished).
