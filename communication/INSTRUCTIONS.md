# Agent-to-agent communication

Five agents work on this repo, one per person: **A** lead/app/kit, **B** speech, **C** core rules, **D** core suggestions/flow/storage, **E** data/eval/pitch (roles: `docs/team-split.md`). They cannot talk live. They talk through files in this folder, on the `communication` branch.

## Layout

```
communication/
  INSTRUCTIONS.md          this file
  A-lead-kit-app/   STATUS.md   messages/
  B-speech-engines/ STATUS.md   messages/
  C-core-rules/     STATUS.md   messages/
  D-core-suggestions/ STATUS.md messages/
  E-data-eval/      STATUS.md   messages/
```

## The one rule: write only in your own folder, read everyone else's

- You write **only** inside your own folder. Never edit another agent's folder, even to fix a typo; send them a message instead.
- To send a message, create a new file in **your own** `messages/`. The recipient finds it by reading your folder.
- Messages are **append-only**. Never edit or delete a sent message. To correct one, send a new message that says "Correction to <filename>".
- `STATUS.md` is the exception: a snapshot you overwrite, not a log.

## Message files

Name: `NNN-to-<recipients>-<short-slug>.md`
- `NNN` is your own running counter (001, 002, ...), per sender.
- `<recipients>` is letters joined by `-`, or `all`. Examples: `003-to-A-api-ready.md`, `004-to-C-D-type-change.md`, `005-to-all-freeze.md`.

Content (keep it short, one topic per file):

```
From: B   To: A, C   Date: 2026-10-11 14:30 IST   Re: <filename or "-">
Type: info | request | decision | blocker | done

<What you need or what changed, in a few lines. Name exact files, classes,
branches and commit hashes. State what you want the recipient to do, and by when.>
```

Types: `request` expects an answer; answer with a new message whose `Re:` names the request. `blocker` means you cannot continue without it. `done` means something others depend on is now merged or pushed. Without an answer to a request, assume nothing.

## Reading (do this at the start of every task, and again before you finish)

1. `git fetch --all`, then read the other agents' folders from their latest state. Their messages live on the `communication` branch (other agents may also commit on it; run `git pull origin communication` or merge it).
2. Read every other agent's `STATUS.md`.
3. In each other agent's `messages/`, read every file whose name is addressed to your letter or `all`, and that is newer than the last one you handled. Ignore files addressed to others, except `all`.
4. Handle requests addressed to you: do the work, or reply with a reason and a date. Never silently skip one.
5. Record in your own `STATUS.md` the highest message number you have read from each agent (add a line like `Read up to: A=004 B=007 C=002 D=000 E=001`).

## Writing

1. Write a message when you: change a shared interface or type, finish something another agent waits on, are blocked, need a decision, or find a defect in another agent's work.
2. Update your `STATUS.md` whenever your state changes.
3. Commit only your own folder, with a message `comm(<letter>): <subject>`, and push the `communication` branch. Do not mix code changes in the same commit.
4. Code changes go on your own work branch as before (`docs/team-split.md` §5). Only A merges to `master`. Messages point at code; they do not replace it.

## Authority and conflicts

- Interface changes to `:core` types go through C and are announced to `all` before merging.
- Only A merges to `master`, installs on the kit, and pushes to the submission repo.
- If two messages disagree, the newer one wins unless A has sent a `decision` message.
- Never put secrets, credentials or the confidential kit guide in a message.
