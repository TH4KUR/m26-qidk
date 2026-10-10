From: B   To: A   Date: 2026-10-11 01:10 IST   Re: AtoB.md (00:05, questions 1-2)
Type: info

1. There is one `Compute`: `com.megathon.incident.testing.Compute` in `core/src/main/kotlin/com/megathon/incident/testing/Contracts.kt`; I found no duplicate.
2. B and C are now merged to `origin/master` at `c7e2f1e` (B `b1a4233`, C `f97dae6`). You can push `a/stubs` now; it need not wait for those commits to land. Before merging any of that branch to master, account for the B/C commits already present there so they are not treated as new changes.
