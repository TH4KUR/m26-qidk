# Hindi strings: Review screen, Saved screen, spoken-summary player

Model-authored, short, spoken-style Devanagari; not reviewed by a native speaker. Every string below is passed through
`tr(en, hi)` unless the row says otherwise. Field labels, type, severity, status words and dates come from `HindiLabels` /
`HindiDate`, which other workers fill; this file only lists what this screen authors itself.

Source files: `app/src/main/kotlin/com/megathon/incident/ui/screens/CheckScreens.kt` (Review = `CheckScreen`, Saved = `SentScreen`),
`app/src/main/kotlin/com/megathon/incident/ui/components/SummaryPlayerCard.kt`.

| English | Hindi | where |
|---|---|---|
| Review your report | रिपोर्ट देख लें | Review: top bar title |
| One last look. | एक बार देख लें। | Review: headline (compact and wide) |
| Built from your words and kept on this device. Check the details, then save. | आपकी बातों से बनी है और इसी डिवाइस पर रहती है। जानकारी देख लें, फिर सेव करें। | Review: subtitle (compact) |
| Your report stays on this device. Check the details before saving. | आपकी रिपोर्ट इसी डिवाइस पर रहती है। सेव करने से पहले जानकारी देख लें। | Review: subtitle (wide) |
| Assistant-written details are labelled. | सहायक की लिखी जानकारी पर लेबल है। | Review: note (wide) |
| Still missing | अभी बाकी है | Review: attention card title |
| . You can save without it, or go back and add it. (after the list of missing labels) | । इसके बिना भी सेव कर सकते हैं, या वापस जाकर जोड़ सकते हैं। | Review: attention card body tail |
| Still missing:  (before the list) | अभी बाकी:  | Review: one-line missing note (wide) |
| Not mentioned:  (before the list) | नहीं बताया:  | Review: quiet line under the panel |
| Your report | आपकी रिपोर्ट | Review and Saved: panel title |
| Assistant-written | सहायक ने लिखा | Review and Saved: pill on assistant-filled rows |
| Not mentioned | नहीं बताया | Review and Saved: row placeholder when nothing was said |
| Needed (row placeholder) | `HindiLabels.status(NEEDED)`, so it matches the status chip | Review and Saved: row placeholder |
| Don't know (value of an "unknown" field) | पता नहीं | Review and Saved: row value, status Not sure |
| None (value of an explicit "none" field) | कोई नहीं | Review and Saved: row value, status None |
| Save report | रिपोर्ट सेव करें | Review: primary button |
| Save with missing details | बाकी जानकारी के बिना सेव करें | Review: primary button when a required detail is missing |
| Keep editing | और बदलाव करें | Review: secondary button |
| Complete | पूरा हुआ | Saved: top bar title |
| Report saved. | रिपोर्ट सेव हो गई। | Saved: headline |
| Saved on this device. Nothing was sent anywhere. | इस डिवाइस पर सेव हुई। कहीं भेजी नहीं गई। | Saved: privacy sentence |
| Reference  (before the 8-character code) | संदर्भ  | Saved: reference line (the code itself is not translated) |
| New report | नई रिपोर्ट | Saved: button |
| Hear it again | फिर से सुनें | Saved: text action (no player) |
| Stop speaking | बोलना बंद करें | Saved: text action (no player) |
| Spoken summary | बोला गया सारांश | Player: panel title |
| Preparing the audio… | ऑडियो तैयार हो रहा है… | Player: preparing state |
| Audio is not available for this voice. | इस आवाज़ में ऑडियो उपलब्ध नहीं है। | Player: unavailable state |
| Try again | फिर कोशिश करें | Player: unavailable state action |
| Summary position | सारांश की स्थिति | Player: seek bar content description |
| 0:12 of 0:40 | ०:४० में से ०:१२ | Player: seek bar state description (Devanagari numerals) |
| Pause summary | सारांश रोकें | Player: pause button description |
| Play summary | सारांश चलाएं | Player: play button description |
| 0:12 / 0:40 (time labels) | ०:१२ / ०:४० | Player: elapsed and total time, Devanagari numerals |
