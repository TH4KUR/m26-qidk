import json, sys, statistics as st, collections
sys.path.insert(0, "/tmp/claude-1000/-home-th4kur-Desktop-m26-qidk/4020479d-b666-4a56-880e-9050f25d9d98/scratchpad/e-eval")
from score import score, render_markdown
run_dir = sys.argv[1]
golds = [json.loads(l) for l in open("/tmp/claude-1000/-home-th4kur-Desktop-m26-qidk/4020479d-b666-4a56-880e-9050f25d9d98/scratchpad/e-eval/gold_v1.jsonl") if l.strip()]
dev = [g for g in golds if g["split"] == "dev"]
preds = [json.loads(l) for l in open(f"{run_dir}/predictions.jsonl")]
raw = [json.loads(l) for l in open(f"{run_dir}/raw.jsonl")]
print(render_markdown(score(dev, preds), run_dir.rsplit("/", 1)[-1]))
turns = collections.defaultdict(list)
for r in raw: turns[(r["gold_id"], r["turn"])].append(r)
final_ok = sum(1 for v in turns.values() if v[-1]["valid"])
first_ok = sum(1 for v in turns.values() if v[0]["valid"])
print(f"turns: {len(turns)}; valid on the first call: {first_ok}/{len(turns)} ({100*first_ok//len(turns)}%); valid after the one repair retry: {final_ok}/{len(turns)} ({100*final_ok//len(turns)}%)")
t=[r["elapsed_ms"] for r in raw if "elapsed_ms" in r]; tt=[r["ttft_ms"] for r in raw if "ttft_ms" in r]; d=[r["decode_tps"] for r in raw if "decode_tps" in r]; g=[r["generated_tokens"] for r in raw if "generated_tokens" in r]; p=[r["prompt_tokens"] for r in raw if "prompt_tokens" in r]
print(f"per model call: n={len(t)}; elapsed median {st.median(t):.0f} ms, p90 {sorted(t)[int(len(t)*0.9)]:.0f} ms, max {max(t):.0f} ms; first token median {st.median(tt):.0f} ms; decode median {st.median(d):.1f} tok/s; generated tokens mean {st.mean(g):.0f}, max {max(g)}; prompt tokens max {max(p)}")
per_turn = [sum(x["elapsed_ms"] for x in v if "elapsed_ms" in x) for v in turns.values()]
print(f"per turn (incl. retry): median {st.median(per_turn):.0f} ms, max {max(per_turn):.0f} ms")
print("errors:", sum(1 for p in preds if p.get("error")), "| compute:", dict(collections.Counter(p["compute"] for p in preds)))
