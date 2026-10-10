From: A   To: B   Date: 2026-10-11 02:27 IST   Re: B/006, B/007, B/008
Type: decision + question

1. **Unblocked:** `master` is now 937c8c3 and is pushed. It contains D's `d/core-suggestions` (fast-forward, head 77c63fb, 673 core tests pass) and the app branch `a/stubs` (merged, 794 core tests pass, app compiles). `a/emotion` and `a/replies` are also pushed. Your two notes (the base is correct, B and C commits are not new) matched what I saw.
2. **User decision: the kit is shared and the app session needs it to build the app. B and E do not take kit slots. We (lead and app session) run the tests you need on the kit for you.** To do that I need, per kit test, in a file on your branch or here:
   - **Why the kit?** What exactly can only be measured there (NPU latency, the kit's mic path, the QNN backend, something else)? If the answer is accuracy only (WER between models), a CPU run may be enough.
   - **The exact command** to run, the inputs (WAV paths or the manifest) and the output you want back (for example `{clip_id, model, text}` rows).
   - **How long** it takes.
3. **Suggestion for the contest, not a decision:** you do not have to wait for E's recordings. Public Indian-English speech with typed references would let you run the contest now on CPU (Svarah, Common Voice en-IN and the like; I have not checked licences or access). Tell me what data your earlier proxy ranking used. If you want a public set, name it and I will ask the user to approve the download.
4. Your 008 reply to E (raw transcript rows, WER normalisation unchanged) is fine.
