## D1: Execution scope

**Choice:** All 20 issues on a single branch (issue-438-neocortex-audit-wiring)
**Alternatives:**
- Per-tier branches — cleaner dependency ordering but coordination overhead for mechanical fixes
- Per-issue branches — maximum isolation but extreme overhead for XS/S fixes
**Rationale:** Fixes are well-defined by audit; single branch minimizes context switching and is fastest to execute
**Trade-offs:** Large diff on the branch; mitigated by per-issue commits with clear references
**Sources:** casehubio/neocortex#438 epic issue body
**Exploration:** quick
**Status:** captured

## D2: Commit and ordering strategy

**Choice:** One commit per issue, landed in tier order (Tier 1 → Tier 2 → Tiers 3+4)
**Alternatives:**
- One commit per tier — fewer commits but large diffs, harder to bisect
- Free-for-all with squash cleanup — fastest but loses per-issue traceability
**Rationale:** Matches audit granularity, gives clean bisectability, respects tier dependency ordering. XS issues are naturally one commit; larger issues (#444, #446, #450) may need 2-3.
**Trade-offs:** More commits than per-tier approach; acceptable since each is small and well-scoped
**Sources:** casehubio/neocortex#438 tier structure
**Exploration:** quick
**Status:** captured

## D3: Cross-repo wiring (#444)

**Choice:** Neocortex-side changes land on this branch; create a separate blocks issue for the configureAppraisal()/configureGutFeeling() call
**Alternatives:**
- Include blocks changes on this branch without a linked issue — violates commit-references-issue rule
- Defer #444 entirely — leaves the most impactful fix unstarted
**Rationale:** Blocks is in the same slot so the work can happen this session, but blocks commits need their own issue per project conventions
**Trade-offs:** Two issues to close instead of one for #444; but the split is clean (neocortex owns the API, blocks owns the call site)
**Sources:** CognitionCore.java:549-568 (configureAppraisal/configureGutFeeling methods)
**Exploration:** quick
**Status:** captured
