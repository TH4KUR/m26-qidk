# time_corpus_v1.jsonl

A measuring corpus for incident-time extraction (`TemporalResolver`). 132 items: 42 English, 48 Hindi (Devanagari),
42 Hinglish (Roman).

**MODEL-AUTHORED. NOT REVIEWED BY A NATIVE SPEAKER. Written before any result was seen.** The Hindi and Hinglish
sentences, and some expected values (listed below under "contestable"), need a native reviewer before anyone treats
a score as more than an engineering signal. The expected values were fixed by reasoning, not by looking at what the
resolver outputs.

Each line: `id`, `lang` (en | hi | hinglish), `category`, `capturedAt` (ISO with +05:30; always Sunday 2026-10-11:
08:00, 00:10, 23:50 or 13:30), `sentence` (a full report-style sentence), `expected` (null = no incident time).

`expected` fields: `date` (ISO or null), `time` (HH:mm or null), `partOfDay` (MORNING | AFTERNOON | EVENING | NIGHT or
null), `approximate`, `ambiguous`, and for a week range `from`/`to` (Monday-start ISO weeks; `date` stays null).

## Rules the expectations follow (the app's existing policy)

- A part of day alone never becomes a clock time (`time` null). A bare clock time or part of day with no day word keeps
  `date` null (the app asks "Was that today?").
- A clock time with no am/pm and no part of day keeps its literal 12-hour reading and is `ambiguous`.
- A week is a range, `approximate`; "N weeks ago" is one approximate date; "N days ago" is an exact date with no time.
- "kal", "parso", "narso" are ambiguous (`date` null) UNLESS the sentence is in the past tense (tha/thi/hua/gaya/laga/
  kiya ...), which makes them yesterday / the day before yesterday / three days ago, not ambiguous.
- A future statement (hoga, karunga, will, scheduled, tomorrow) is not an incident time: expected null.
- Two times in one sentence: the incident clause wins; a time attached to a response verb (called, informed, reported,
  bulaya, bataya, phone kiya) is not the incident time.
- Weekdays resolve to the most recent such day strictly before the capture day ("pichhle" gives the same day).
- "Last night at 1 am" / "kal raat ek baje" is the early hours of the NEXT calendar day (the capture day here).

## Contestable expectations (a reviewer should look at these first)

- Weekday resolution (most recent past occurrence; "last Friday" said on a Sunday could also mean the Friday of last week).
- Midnight rollover items (`midnight` category) and "late last night after midnight".
- "narso" as three days ago; "parso" as two days ago (both can also be future words; the corpus uses past-tense sentences).
- "kuch der pehle": capture date, no time, approximate.
- `approximate` for a count of days ("do din pehle") is false; for weeks it is true (existing tested policy).
- `aaj 10 baje` captured at 13:30: 10:00 only, because 22:00 would be in the future.

## How it is measured

`core/src/test/kotlin/com/megathon/incident/domain/TemporalCorpusTest.kt` writes `core/build/time-corpus/report.txt`
(tables per language and category, plus every miss). A result is CORRECT only if date, time, part of day, approximate,
ambiguous (and the range) all equal the expectation. Modes: `sentence` (resolve the whole sentence), `phrase`
(`findTimePhrase` then resolve, what the app does when the model forgot `date_time_text`), `context` (the found phrase
resolved with the sentence as tense context).

## Results (this corpus, model-authored; the fixes were written after the baseline was seen, so "after" is in-sample)

time_corpus_v1 (132 items), CORRECT / n:

| mode | en baseline -> after | hi baseline -> after | hinglish baseline -> after |
|---|---|---|---|
| sentence (resolve the whole sentence) | 28/42 -> 42/42 | 10/48 -> 48/48 | 19/42 -> 42/42 |
| phrase (findTimePhrase, then resolve; no tense) | 22/42 -> 40/42 | 5/48 -> 40/48 | 8/42 -> 36/42 |
| context (found phrase + sentence tense) | n/a -> 40/42 | n/a -> 48/48 | n/a -> 42/42 |

Baseline sentence mode: 57 correct, 27 wrong, 43 null-when-a-value-was-expected, 5 spurious-value-when-null-expected.
The two remaining context misses are the bare "just now" / "right now" items, which findTimePhrase leaves to ReportSession.

time_corpus_probe_v1 (46 items) was written after the fixes from phrasings they were not built around. First run: sentence
41/46, context 35/46. Seven generic gaps it showed (noon/midnight, "at 3 meters", "raat ke barah baje", "beeti raat",
a time + "se" = since, a trailing "ke kareeb", weekday + part of day) were then fixed; final sentence 46/46, context 46/46.
