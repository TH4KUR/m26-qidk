# How the audio is scored

Bayaan shows, for every spoken turn of a report, a score that is written out term by term. It is a **heuristic**: it
describes how well the device captured the voice and how the voice (and the words) fed the priority rules. It is not
speech accuracy and it is not a measure of how serious the report is.

Code: `core/.../scoring/AudioScore.kt`, `PriorityExplainer.kt`, `SessionLog.kt` (pure, unit-tested on the JVM).
Screen: the "How this ran" sheet, "This session" view, card "Audio score".

## 1. Capture quality (0 to 100)

Computed from the clip's own 16 kHz PCM16 samples. Five terms; each shows the measured value, the rule, and the points
out of its weight. The total is the sum of the points. Levels are dBFS (0 dBFS is full scale).

| Term | Weight | Measured as | Full points | Falls to 0 |
| --- | --- | --- | --- | --- |
| Peak level | 25 | highest `abs(sample)` in dBFS | at or above -24 dBFS | at -48 dBFS, straight line |
| Average level (RMS) | 25 | RMS of all samples in dBFS | -42 to -12 dBFS | at -60 dBFS below the band, at -3 dBFS above it |
| Clipped samples | 20 | share of samples with `abs(sample) >= 32767` | 0 % | at 1 % of samples |
| Length | 15 | samples / 16 000 | 2 s or longer | at 0.5 s |
| Silence | 15 | share of 20 ms frames whose RMS is below -50 dBFS | 30 % or less | at 80 % |

Points are `round(weight * fraction)`, with `fraction` clamped to 0..1 and linear between the two end points.

Where the numbers come from:

* -42 dBFS is the lower edge of "level looks fine" already used on the Record check screen; the kit microphone's typical
  mean for normal speech is about -39 dBFS, so a normal clip earns the full level points.
* -50 dBFS is the floor of the microphone level meter, so "silence" here means "what the meter shows as nothing".
* The peak term rewards enough signal; it deliberately does not punish a loud peak. Loudness at the top is judged by the
  clipping term and by the average-level band.

With no samples every term says "not measured" and there is no total. Nothing is filled in.

## 2. Recognition facts

| Fact | Source |
| --- | --- |
| Engine | the engine's own name (`SpeechEngine.name`) |
| Ran on | the compute the engine reported for that result (NPU, CPU, HYBRID, SIMULATED); never a constant |
| Time to recognise | the engine's own elapsed time for that result |
| Speed against real time | audio length divided by that time (5.0x means five times faster than it was spoken) |
| Words recognised | whitespace-separated tokens with at least one letter or digit |
| Recognition confidence | always "not measured" |

What is **not** measured, and is said so on screen:

* **Confidence.** None of the speech engines (Whisper through sherpa-onnx, Nemotron Hindi) returns a confidence number
  through this app, so none is shown and none is estimated.
* **Accuracy.** There is no reference transcript for a live clip, so word error rate cannot be computed on the device.
* **A simulated engine's time.** The scripted demo engine's elapsed time is not a measurement, so for SIMULATED the time
  and the speed show "not measured".
* **Live windows.** In live mode the time is the sum of the engine's time over every preview window and the final pass,
  not one pass over the clip. The speed is therefore lower than one batch pass over the same audio would give. The engine
  name carries "(live windows)" so the two are not confused.

## 3. How the voice fed the priority

The priority advice is `impact (1 to 5) x immediacy (1 to 4)`, a score from 1 to 20, mapped to LOW (1 to 4),
MEDIUM (5 to 9), HIGH (10 to 14), URGENT (15 to 20). This is the app's default `risk-matrix` strategy.

* The **words** set impact and immediacy (hazard phrases, tense, negation, distress cues, people at risk).
* **Voice tags** only raise: a sound event that is on the rule list (`hazards.json`, `signals`) sets a minimum impact
  and a minimum immediacy (a gunshot: impact at least 5, immediacy at least 4); an emotion tag in `distressEmotions`
  (angry, sad, fearful) counts as distress, which adds 1 to immediacy, at most 4. A tag with no rule is listed and
  changes nothing.
* Nothing in the audio lowers the score.

The card lists each audio-derived input with its source, the rule that applies, and the points it added. A tag's points
are what the 1 to 20 score loses if that one tag is removed and the rules are run again ("leave one out"). The words
act together, so they are shown as their own terms and summarised as "Words alone". Then "Words and voice tags" and
"Voice tags added" (the difference). No transcript text is kept: a hazard cue's quoted phrase is stripped from its label.

This is a **replay** of the app's own rules (`UrgencyFeatureExtractor` and the default strategy) on this report's words
so far and every voice tag heard so far. The live priority can be higher: the language model's hint and the reporter's
answers also count, and the live priority never goes down during a report. The replay never changes a stored value and
never feeds back into the report.

## 4. The log of saved reports

When a report is saved, one line is appended to `filesDir/logs/sessions.jsonl`:

```
{"v":1,"atEpochMs":...,"reportId":"report-...","stages":[{"stage":"Hear","engine":"...","compute":"NPU","ms":900}],
 "audio":[{"capture":88,"peakDbfs":-14.2,"meanDbfs":-27.5,"clippedPct":0.0,"silencePct":31.0,"durationS":4.2,
           "engine":"...","compute":"NPU","processingMs":900,"realTimeFactor":4.7,"words":12,"rulesScore":12,"wordsOnlyScore":9}]}
```

Numbers, engine names and compute only: no transcript, no names, no places. Lines are only appended. A damaged line is
skipped when reading. The same report saved twice counts once (the later line wins).

The "All sessions" view aggregates this file (`SessionLogAggregator`):

* **Reports saved**: distinct report ids.
* **Typical time per step**: the median of every timed run of Hear (speech), Read (model), Tags (voice tags) and Speak
  (spoken replies), pooled over all reports. For an even count, the mean of the middle two. A step with no recorded time
  says "not measured".
* **Where the work ran**: how many stage records ran on each compute, with its share.
* **Average audio score**: for each report that was scored, the mean of its turns' capture scores; then the mean over
  those reports. Reports with no audio score are left out and the count is shown.
* **Latest reports**: the newest ten, one line each: time, audio score, speech and model times, compute.

The Speak stage of the summary read out after saving is recorded after the line is written, so a report's line holds the
spoken questions' Speak times but not the closing summary's.
