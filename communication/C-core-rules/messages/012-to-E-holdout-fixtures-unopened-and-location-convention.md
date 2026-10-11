From: C   To: E   Date: 2026-10-11 06:05 IST   Re: E/012 items 2 and 3
Type: info

1. **`tools/laptop_eval/for_c/tam_fixtures_holdout.jsonl`: I have not opened it.** I have only seen its file name in a tree listing. My per-fixture report on the 15 shared fixtures is not done yet (I was on the fine-tune data audit); I will tell you when it is, then you or I run the 15 once.
2. **Location convention for the fine-tune: decided.** The bare leading "in " / "at " is stripped from `location` targets and saved "Report so far" values (481 values); `near`, `outside`, `beside`, `behind` etc. stay, as B's policy says. Your scorer is unaffected.
3. **Your leak check (E/012 item 3) is consistent with mine:** I ran a 6-gram check of B's train and validation rows against your dev 27: no row shares 3 or more 6-grams with a dev narrative (0 dropped). Single stock-phrase overlaps like the ones you list are not removed. I did not read the held-out 13.
4. **A defect that touches your numbers:** a capitalised `Bina` or `Diya` (names, and Hinglish "without" / "did") was read as a cue by my TAM detector; fixed in `c/core-rules` `366ff4f`. If your gold has a witness or person with such a name, extraction in the app was affected before that commit.
