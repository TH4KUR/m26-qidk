# Incident UI revamp

User asked for a complete redesign, substantially more beautiful, natural and smooth than the incremental pass. They delegated design decisions and requested final visuals only after completion.

Native Compose. Default graphite canvas, warm white type, mint primary actions, coral recording state, amber uncertainty. System fonts; no image assets, blur, video, particle engines or new dependencies. A custom geometric recorder illustration is static at idle and reacts to real audio during capture.

Flow: Capture → Shape → Review. Each screen uses the same brand header and three-stage trail. Home offers one main record control and optional example help. The listening and processing states morph within the same surface. Report essentials are compact individually expandable cards; evidence and editing are explicit. Follow-ups show one prompt at a time; backend contracts remain unchanged. Review makes local saving explicit. Saved screen gives a finite confirmation animation.

Motion uses brief opacity, translation and scale transitions, press feedback, state morphs, expandable evidence and animated progress. Respect system reduced motion. Never animate indefinitely at idle. No UI screen change, questions or speech triggered while listening. Preserve AppUi.liveText, readiness, and unsaved provisional fields. Preserve priority controls, evidence, undo, suggestions, correction and proof sheet.

Verify native build for localUi and QIDK; actual emulator capture, follow-ups, field editing, review/back, local save, restart, landscape and large font. Inspect final rendered screenshots privately. Record UI-only idle/recording performance honestly. Do not claim QIDK runtime performance from emulator. Preserve Claude's files and all unrelated worktree edits. No commits or branch changes.

## User refinement: cleaner surfaces
The user approved the microphone treatment and rejected the density of rounded boxes. Report fields now use open rows and thin dividers; suggestions use plain text and explicit Accept/Dismiss actions; quick answers use fine underlines, with no rounded containers. Standard review content is also unboxed. The microphone geometry and primary actions retain their distinctive shapes.
