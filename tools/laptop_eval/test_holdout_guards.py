"""The held-out 13 are spent (A/027): the tools refuse to run on them or to rewrite the gold without a confirm flag."""

import pytest

import build_gold
from run_eval import select_items

ITEMS = [{"id": "g01", "split": "dev"}, {"id": "g03", "split": "holdout"}]


def test_dev_runs_without_a_flag():
    assert [i["id"] for i in select_items(ITEMS, "dev", confirm_holdout=False)] == ["g01"]


def test_holdout_and_all_refuse_without_confirm():
    for split in ("holdout", "all"):
        with pytest.raises(SystemExit):
            select_items(ITEMS, split, confirm_holdout=False)
    assert len(select_items(ITEMS, "all", confirm_holdout=True)) == 2


def test_build_gold_refuses_to_overwrite_without_confirm(tmp_path):
    out, out_h = tmp_path / "g.jsonl", tmp_path / "h.jsonl"
    with pytest.raises(SystemExit):
        build_gold.write(confirm=False, out=out, out_homophones=out_h)
    assert not out.exists()
    build_gold.write(confirm=True, out=out, out_homophones=out_h)
    assert len(out.read_text(encoding="utf-8").splitlines()) == 40
