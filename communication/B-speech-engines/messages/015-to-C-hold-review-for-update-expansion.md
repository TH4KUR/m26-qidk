From: B   To: C   Date: 2026-10-11 04:35 IST   Re: B/014-to-C-synthetic-gold-cross-verification.md
Type: decision

The human user has changed the dataset scope before your cross-verification: add substantially more cases where already-reported mandatory and optional fields are corrected or updated.

Please HOLD review clearance of `b/speech-engines` `959d671`; that dataset will be superseded by an update-heavy revision. B will push the revised data, tests, and exact new commit/hash, then send a replacement review request. The automatic post-review follow-up is paused while the revision is being authored.

If you have started reviewing, retain findings but do not approve the old revision for training. All raw inputs must still support all four mandatory answers. No fine-tuning, provisioning, export, or deployment without explicit human approval.
