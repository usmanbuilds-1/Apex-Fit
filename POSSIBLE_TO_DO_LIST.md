# Apex Fit — Possible To-Do List

Last updated: 2026-11-27

## Purpose and scope

This is a living **candidate backlog**, not an approved implementation plan or release commitment. Entries may be explored, redesigned, narrowed, expanded, deferred, combined, removed, or rejected after review. Priorities below are provisional.

- Adding an item here does not authorize changing production code, removing a feature, merging a PR, or changing the database.
- Confirm scope before implementation. Preserve working functionality unless its removal is explicitly agreed.
- Separate visual/UX proposals from correctness issues. Known data-integrity and calculation defects should be evaluated before release; do not silently dismiss them as aesthetic preferences.
- Keep the app offline-first, dark-only, fast and accessible. Do not introduce paid APIs or unnecessary dependencies.
- For approved changes, use a dedicated branch/PR, regression tests, screenshot comparisons where relevant, and device QA. Merge only after explicit approval.

## Current coordination — 2026-11-27

**Owner of this backlog:** engineering ChatGPT session. Update this file when scope, evidence, approval or completion changes; do not maintain competing checklists. Codex/Work provides visual and local QA evidence for this file.

- Engineering ChatGPT: source audit, scientific research, technical specifications, shared data contracts, migration design, domain implementation after approval, automated tests, independent review and integration.
- Codex/Work (Luna): Stitch previews, UI/UX, screen inventory, approved Compose changes, local Android renders and interaction QA.
- User: approves each screen, feature scope, implementation and merge.
- Current gate: planning and preview only. No Android changes for this scope until approved. Today approval does not authorize other screens.
- Last verified remote refs: main `02d5c6dfbf974fda8b7f1dc9c6c9e18b1daf48e7` before this documentation update; Codex branch `codex/night-ledger-qa-20261124` at `19039d0c77a0f33fe9d1337a091796651bc20b57`. Local/uncommitted state is unverified here.

### Active work tracker

| ID | Deliverable | Owner | Status |
|---|---|---|---|
| E01 | Audit 14 features against reachable source | Engineering | Complete (targeted source audit; device verification pending) |
| E02 | Research competitors and scientific/technical evidence | Engineering | Complete (targeted evidence review) |
| E03 | 14-row specification, contracts, dependencies, migrations and acceptance tests | Engineering | Draft complete; awaiting user review |
| E04 | Shared analytics architecture and implementation order | Engineering | Draft complete; awaiting user review |
| E05 | Database migration, privacy and backward compatibility plan | Engineering | Draft complete; awaiting user review |
| E06 | Independent sanity check of technical plan, citations and source risks | Engineering | Complete; revised plan awaiting approval |
| U01 | Repair Today athlete binding and inspect actual Stitch render | Codex/Work | Assigned; result unverified |
| U02 | Inventory screens and map features to UX destinations | Codex/Work | Assigned; result unverified |
| G01 | Review preview and technical plan; approve or revise scope | User | Revised engineering plan approved 2026-11-27; Today preview result unverified |
| G02 | Implement approved domain and UI work on coordinated branches | Both | Engineering P0 approved; draft PR #39 open; UI redesign requires separate screen approval |
| G03 | Regression, migration, render, device and integration QA | Both | Blocked by implementation |
| G04 | Final merge authorization | User | Pending |

### Proposed 14 features — compact scope register

All are candidates, not approved implementation. Existing partial implementations must be verified before deciding what to change.

| ID | Feature | Proposed behavior |
|---|---|---|
| F01 | Muscle volume graphs | Weekly/monthly/yearly direct working-set trends, using full history. |
| F02 | Muscle heatmap | Interactive front/back map and accessible ranked list; counts reconcile with sets. |
| F03 | Muscle set targets | Editable weekly targets per muscle, independent of prescriptions. |
| F04 | Monthly volume comparison | Compare periods with partial-month and zero-baseline handling. |
| F05 | Training frequency | Distinct trained dates per muscle and average intervals. |
| F06 | Training reports | Weekly/monthly strength, volume, adherence and earned PRs tied to sessions. |
| F07 | Workout calendar | Completed, scheduled and missed sessions using preserved schedule history. |
| F08 | Exercise strength graphs | Actual load, reps, estimated 1RM and volume load with intact set pairing. |
| F09 | Rep-specific PRs | Heaviest qualifying set per exact rep count, with backfill/ties/corrections. |
| F10 | Relative strength | Movement-appropriate performance relative to body weight known at workout date. |
| F11 | Performance goals | Load, rep or volume goals backed by qualifying records. |
| F12 | Progress photos | Private on-device dated photos, thumbnails, comparison, deletion and backup policy. |
| F13 | Supersets/circuits | Ordered groups across builder, importer and execution, with resume and real rest timing. |
| F14 | Advanced set types | Explicit warm-up, working, drop, failure and rest-pause accounting; preserve legacy flags. |

Previously discussed unilateral tracking, muscle feedback, mesocycles, equipment profiles and Health Connect remain optional future candidates outside the current 14-feature scope.

**Maintenance rule:** record each milestone once, with commit/PR/test or preview evidence. Distinguish proposed, assigned, verified, approved, implemented, tested, deferred and rejected. Update on each relevant work session or reported Codex milestone; do not assume its local progress. The draft technical specification is [Training Analytics Technical Plan](docs/TRAINING_ANALYTICS_TECHNICAL_PLAN.md), originally committed as `ccf1db92ecddf509e81ff6ff18e042e744f7d870`, fact-checked and revised in `3396a74d31bf4d6da73fd6c4869cd649a03999bc`. It contains the pinned source audit, competitor/scientific evidence, 14 feature specifications with acceptance tests, unified analytics architecture and proposed additive migrations. No Android code or schema changed. Codex branch comparison: two unique commits, 97 behind the audited main baseline; reconcile without overwriting local changes before implementation. Device, migration, CI and Stitch verification remain pending. Approval decisions include partial-workout eligibility, load conventions, estimated 1RM, target defaults, historical schedule rules and photo privacy/backup. All F01–F14 remain candidates pending scope approval.

## Candidate UX and design improvements

### P0 — Shared design system and navigation

- [ ] Audit all screens for inconsistent typography, spacing, colors, icon styles, component shapes and interaction patterns.
- [ ] Establish a coherent dark theme with one restrained accent. Explore the proposed charcoal/muted-mint palette, but do not treat mockup colors as approved replacements for existing tokens.
- [ ] Prefer flat editorial layouts, clear hierarchy, whitespace and subtle dividers over nested cards, decorative containers, gratuitous gradients, glowing borders and excessive badges.
- [ ] Use display typography selectively, readable body text and tabular numerals where useful; avoid monospace for ordinary descriptions.
- [ ] Standardize spacing (candidate scale: 4/8/12/16/24/32 dp), touch targets, contrast and accessibility.
- [ ] Unify headers, navigation, back behavior, bottom sheets, dialogs, transitions, loading/empty/error states and save feedback.
- [ ] Remove redundant APEX FIT branding and repeated content where it provides no value.
- [ ] Preserve scroll position and selected tabs; avoid whole-screen loading for individual section updates.
- [ ] Validate visual consistency on small and large Android screens and with larger font settings.

### P0 — Progress restructuring

Current problem: the overview mixes weight, strength, consistency, recovery and six additional destinations, exposing overlapping information and too many decisions.

Candidate direction:
- [ ] Make Progress answer one question: “Am I improving?”
- [ ] Lead with a concise weight trend, latest measurement, contextual change and one log-weight action.
- [ ] Explore useful time ranges (1W, 1M, 3M, 1Y, All); define their scope and calculate changes from actual dates, not fabricated data.
- [ ] Show a compact training-week summary using the actual schedule, including rotating plans and rest weeks.
- [ ] Consolidate drill-downs into **Body**, **Strength**, and **Training**:
  - Body: weight history, editing/deletion, body measurements.
  - Strength: exercise-specific trends and personal records.
  - Training: workout history, sets/volume, muscle recovery, fatigue and deload details.
- [ ] Avoid duplicate navigation links and repeated metrics; show actionable insights only when relevant.
- [ ] Preserve all existing analytics and history unless a specific removal is approved.
- [ ] Use accurate calendar-spaced charts, daily weight averages and separate latest readings; no fake values or misleading trend claims.

### P1 — Workout execution and rest timer

- [ ] Reduce taps to start, log sets, move to the next exercise, rest and finish.
- [ ] Review automatic transitions, rest-timer controls, interruption/resume behavior and workout completion screens.
- [ ] Explore restrained haptics for meaningful workout interactions.
- [ ] Keep form guidance and necessary instructions available without cluttering the active workout.
- [ ] Ensure timers, partial sessions and saved sets survive navigation and app recreation.

### P1 — Today and Train

- [ ] Today answers “What should I do today?” Prioritize start/continue, rest-day state and useful readiness context.
- [ ] Reduce repeated statistics, lengthy explanations and redundant workout history.
- [ ] Train prioritizes execution, with program management and library access secondary.
- [ ] Keep rotating schedule context clear without adding excessive controls.
- [ ] Audit performance, unnecessary recomposition, loading flicker and repeated data subscriptions.

### P2 — Nutrition, exercise library and plan builder

- [ ] Nutrition prioritizes logging meals and understanding today's targets; move preferences and advanced calculations into appropriate detail views.
- [ ] Evaluate whether fixed clock-time protein timing information is useful or misleading for different training/sleep schedules.
- [ ] Review exercise browsing, search, detail screens, muscle selection, plan editing and import review for clarity and fewer taps.
- [ ] Make warnings actionable rather than presenting a wall of text.

### P3 — Settings, onboarding and secondary screens

- [ ] Group settings by user intent; remove redundant or technical UI that does not help decisions.
- [ ] Review onboarding, validation, empty states and consistency with the main app.
- [ ] Check accessibility, copy, localization readiness and navigation throughout secondary screens.

## Candidate correctness and reliability work

Source audit findings from the merged PR #38 codebase (commit `0b1dcdda`). These are not all device-reproduced; verify each with targeted tests before modifying production behavior.

### High priority candidates

- [ ] Unify readiness, nutrition and adaptive TDEE targets instead of calculating inconsistent targets in independent consumers.
- [ ] Evaluate historical calorie/protein adherence and streaks against appropriate historical targets, rather than today's targets.
- [ ] Make training compliance schedule-aware, including alternating weeks, variable training frequency, rest weeks and distinct training dates.
- [ ] Confirm meal persistence before dismissing the logging interface; handle failures and prevent duplicate submissions.
- [ ] Resolve repeated exercise-instance identity in active workouts so identical catalog exercise IDs do not collide in set logging, progression or UI state.

### Other candidates

- [ ] Align weight validation across logging, editing, Settings, units and repository boundaries; avoid blocking unrelated settings saves.
- [ ] Make weight-save state lifecycle-safe and avoid stale/shared errors, stuck dialogs and misleading deletion feedback.
- [ ] Review final-measurement deletion and legacy baseline handling; avoid silently treating generic default weight as a measured value.
- [ ] Review actual timestamp ordering, time zones and legacy weight records without restricting multiple daily measurements.
- [ ] Add a schedule repair editor for incomplete or ambiguous imported schedules; never invent missing prescriptions.
- [ ] Carry imported unilateral instructions and relevant plan notes into workout execution.
- [ ] Explore linked workout templates or explicit apply-to-all editing for repeated occurrences; preserve intentional per-slot overrides.
- [ ] Correct irregular-date spacing in the Today weight sparkline; verify other charts and time ranges separately.
- [ ] Optimize weight history queries, aggregation, caching and paging as data grows, without losing accessible/exportable history.
- [ ] Make historical Nutrition views use date-appropriate schedule context and clearly distinguish estimated versus recorded targets.

## Proposed validation gates for approved work

- [ ] Full Android unit and regression suite, lint, debug APK build and palette checks.
- [ ] Targeted tests for existing weekly plans, multi-week cycles, exact fragmented paste, multiple daily weight records, edits/deletes and plan switching.
- [ ] Verify migrations preserve existing workouts, nutrition and weight history.
- [ ] Compare screenshots before/after across all affected states (empty, populated, loading, error and active workout).
- [ ] Manual device QA: navigation, process recreation, rotation rollover, offline operation, workout save/resume and meal/weight logging.
- [ ] Measure performance before and after changes instead of assuming improvements.

## Change policy

Use this file as the repository's working list of **possible** future changes. Update entries as ideas are discussed; mark implemented only after verification, record the corresponding PR/test evidence, and move rejected or deferred ideas out of active scope. This file is not a substitute for a confirmed implementation specification.

### Sanity-check outcome (2026-11-27)
- [x] Verified cited research and competitor feature claims; narrowed scientific claims and distinguished product policy from evidence.
- [x] Hardened plan for active JSON compatibility, duplicate-save prevention, effort provenance, historical PRs, time ranges, schedule history and photo privacy.
- [ ] Before approved implementation: confirm partial-workout eligibility, strength/load conventions, e1RM policy, optional muscle targets and calendar rules. Start with data-integrity fixes and read-only analytics without unnecessary migrations.
- [ ] Reconcile Codex's local branch before integration (remote comparison before these documentation edits: 2 ahead, 99 behind main). Do not overwrite local work; implementation on a separate branch may proceed only with user approval and coordinated data contracts.
No code, tests or migrations executed in this review. See revised technical plan for specific acceptance gates.

### P0 engineering implementation — 2026-11-27
- [x] Owner authorized proceeding with revised technical plan; UI redesign and merge remain separately gated.
- [x] Opened [draft PR #39](https://github.com/usmanbuilds-1/Apex-Fit/pull/39) on `chatgpt/p0-workout-persistence-20261127` (P0.1 verified workflow head `73efacc22f72dfbf2184195f81393ff89a6b7106`; P0.2 verified workflow head `f620ccc1bbc52403248b4cf1d280972076953837`; P0.3 head `a0dff904fcfa7f857b8991a9f2e2d17e18dc962e`; P0.4 head `d83c89f4c48315e3209c220867185916a6a1d4db`; P0.5 head `37ff53c5e3c9aae76288f129d79ffb03686746e3`). First slice only: stable active-workout UUID, versioned recovery without silent deletion, duplicate-write guard, final snapshot before atomic save, PR read/write transaction, cleanup safety; functional recovery dialog; no Room schema change.
- [x] Verify PR #39 Android CI and palette workflow success on head `73efacc22f72dfbf2184195f81393ff89a6b7106`.
- [ ] Inspect individual regression test evidence and run local Android process-death/recovery/device checks before merge. Regression cases authored, not yet verified. Latest head CI run `45947752062` and palette run `45947752180` both completed successfully, including APK packaging, test-result upload and screenshot rendering. Exact individual regression test results and device recovery remain unverified. Follow-up hardening ensures a cancelled Start cannot hide its durable draft and foreground service stops only after successful commit.
- [x] P0.2 active occurrence keys, independent prescriptions/sets, RIR and rest routing, durable draft normalization; code committed to PR #39. Real Room fixture and key/restore tests added; Android CI `45949784766` and palette `45949784666` both succeeded on P0.2 head (APK, test results and rendered screenshots uploaded). Individual XML and actual device interaction remain pending. Repeated entries preserve explicit loads/calibration; automatic progression/adaptive set adjustment paused for repeated catalog IDs because historical occurrence attribution is missing. Ordinary exercise progression unchanged by design. No Room migration; historical occurrence linkage, exact cursor/skip and timer resume still outstanding.
- [x] P0.3 confirmed-set completion/correction uses durable write before publishing, advancement and timer start; rejects stale session callbacks, serializes with final commit and exposes save errors. Actual Room sets feed the normal receipt; ID-verified DataStore journal stages exact earned records before Room transaction and reopens undismissed receipt after restart. Old drafts remain compatible; no Room migration. Tests added for persisted values, stale callbacks, committed receipt recovery and staged but uncommitted receipt rejection. Release reflection fields kept. Code committed; latest-head CI/individual test evidence and device verification pending.
- [ ] Finish P0: verify P0.3 CI and individual tests, injected storage-write failure and process-death device journey; rest observation persistence and exact cursor/skip/timer resume; historical occurrence identity, record provenance and other approved data-integrity fixes. Receipt journal covers only the undismissed latest workout, not permanent historical PR events; coordinate any later Room migration. Coordinate overlapping files with Codex. Do not claim full P0 completed.
- [ ] Begin read-only analytics slice after P0 integrity gate; preserve historical queries and explicit attribution coverage.

### P0.4 historical exercise identity — 2026-11-27
- [x] P0.4 code on draft PR #39: Room v29 additive nullable occurrence index, original set number, source plan exercise ID and observed completion timestamp. New saves retain the separate workout entries; history and receipt presentation groups by recorded occurrence, with catalog fallback for legacy rows. No old occurrence IDs/timestamps invented. Existing catalog-level PR and readiness/fatigue aggregation retained. Automatic progression/adaptive volume remains paused for repeated prescriptions or a known mixed-occurrence latest history; per-occurrence progression policy remains deferred.
- [x] Added v28→v29 and v8→v29 migration tests, legacy data/foreign-key checks, Room save provenance assertions, receipt grouping regression. CI now uploads generated Room schemas. Retrieved compiler schema artifact `15177471009`, inspected diff to v28, committed matching v29 schema in `95db830be90823792c931349805ac6b0b030fda0` (actual Room hash `35e09cbf7a332b3613a52950987dcbc8`). Migration validation still requires a green run.
- [!] P0.3 CI `45950491797` failed (373 tests reported, two failures: TrainViewModelTest RIR correction assertion and AuditRegressionTestV4 initializationError). Palette `45950491767` passed. Source fixes: await durable RIR correction job and explicitly return Unit in receipt recovery test. No assertions removed. Re-run on current head to establish whether fixes resolve both failures and exercise the full regression class.
- [!] P0.4 CI `45951185036` failed; palette `45951185098` passed. Downloaded and parsed XML artifact `15177191511`: 409 tests, 3 failures, 0 errors/skips. TrainViewModelTest 7/7 passed; AuditRegressionTestV4 33/34 passed (repeated-exercise async draft timeout); MigrationTest 19/21 passed (both v29 tests missing schema asset); WorkoutReceiptSummaryTest 4/4 passed. Prior RIR and test initialization failures resolved. Committed compiler-generated schema; changed extra-set creation to durable acknowledgement and retained persisted-set assertions. Re-run needed, not yet proven fixed.

### P0.5 rest and workout resume — 2026-11-27
- [x] Active JSON now stores cursor, explicit skipped occurrence indices and a rest checkpoint (same compatible v2 JSON, nullable fields). Confirmation atomically persists completed set, next cursor and pending rest; skip/next decisions await durable write. Extra sets use acknowledged durable writes. Rest timer reconstructs from saved checkpoint, uses elapsedRealtime/boot provenance and monotonic alarms, persists adjustments and closes against the original set/session. Invalid/rebooted timing is not inferred; existing historical rest=0 remains ambiguous until nullable observation provenance is introduced. Expired timer counts at most the saved countdown interval, not physiological rest. Incomplete/skipped working prescriptions save as partial; earlier completion semantics intentionally corrected. Existing 90-day analytics exclude partial sessions; address actual qualifying work during P1.
- [x] Tests added for skip/restart/cursor, timer adjustments and exact source set, stale checkpoints, expired/restored timing, unknown boot and partial classification. Existing VM integration fixture closes checkpoint before next exercise. No additional Room migration, no visual redesign.
- [ ] Current head `37ff53c5e3c9aae76288f129d79ffb03686746e3` CI at observation: Palette regression check 45951921746 in_progress; Apex Fit Android CI & APK Build 45951921903 in_progress. Full Android CI, migration tests, injected I/O failure, process-death/reboot/notification/device QA and integration with Codex remain required. Historical rest provenance, partial-session analytics, historical PR policy and per-occurrence progression are not complete. No merge/release. Next P0.6 integrity audit and fixing all red checks before P1.

### P0.6 combined integrity audit — 2026-11-27
- [x] Compared combined P0 branch against main; at audit baseline `e4386b98ff81807b16e9a5491eecd70f51a62159`, main-only changes were to this tracker. Codex local workspace not inspected or modified.
- [x] Rechecked actual generated v29 schema ZIP and P0.4 failure XML; confirmed schema hash and all three exact failures previously recorded.
- [x] Corrective changes on PR #39: durable discard guarded by session identity and commit mutex, skip confirmation only after save, final workout save settles open rest checkpoint atomically. Regression tests cover stale discard and finish with pending rest; existing tests await discard acknowledgement. No additional Room migration or visual redesign.
- [ ] Latest head `5f2e0a265ec6a7f84c20d8d9d27d7fd6244d799e` exact-head workflows: Palette regression check 45952446265 in_progress; Apex Fit Android CI & APK Build 45952446255 in_progress. Prior P0.5 palette run `45951921746` passed; Android run `45951921903` still in progress at last observation. Must inspect XML and verify all current-head checks, including v29 migration, before treating P0 as green.
- [ ] Outstanding audit: Settings full-data-reset versus active workout/start/save synchronization, injected disk failure, physical process-death/reboot/alarm/upgrade QA, integration with Codex. Broader nutrition/readiness/compliance/weight correctness backlog remains open. No permanent PR history or full per-occurrence progression yet. P1 not started; no merge.

### P0.6 CI verification — 2026-11-27
- Latest head `5f2e0a265ec6a7f84c20d8d9d27d7fd6244d799e`: palette `45952446265` passed; Android `45952446255` failed. Job `140351176501` logs report 412 tests completed, 2 failures, both TimeoutCancellationException: `committedReceiptRecoversExactPrsAndDatabaseSetsAfterCrashBeforeDraftClear` and `confirmedCommittedDraftIsNotRestoredOrInsertedAgain`. Screenshot rendering and schema/test artifact upload succeeded, APK upload skipped. Root cause of timeouts not yet established. Inspect restoration synchronization and test fixture behavior; do not remove assertions or merely extend timeout. P0 remains unverified; no merge.

### P0.6 recovery timeout correction — 2026-11-27
- [x] Downloaded actual test XML artifact `15177521916`. Both timeouts were at the predicate waiting for the active DataStore draft to become null (AuditRegressionTestV4 lines 1014 and 1217); receipt recovery already reached its receipt assertion path in the second case. No underlying storage exception is shown in XML, so the exact scheduling cause is not proven.
- [x] Recovery now runs on IO, compare-and-clears the exact committed draft and publishes recovered receipt only after acknowledged cleanup. Introduced the restoration completion barrier for deterministic tests; both retain their original storage-null, receipt equality, no duplicate-set and no restore-error assertions. Added stale cleanup isolation/idempotence test. No timeout increase or test removal.
- [x] Corrective head `e2d9c72fb0e76fd75ecc15ecd822da1bf99f980b` exact-head CI: Android `45954028197` and palette `45954028309` both passed. Job `140356221659` built APK, ran unit tests and lint, uploaded schema/test/APK artifacts and rendered Compose screenshots. Downloaded and parsed XML artifact `15178321460`: 413 tests across 56 suites, 0 failures, 0 errors, 0 skipped. Both previously failing recovery tests and new stale-cleanup test passed; AuditRegressionTestV4 37/37, MigrationTest 21/21 (including v28→29 and v8→29), TrainViewModelTest 7/7, RestTimerTest 2/2. Automated regression gate green on exact head.
- [ ] Remaining P0 release gates: full-data-reset synchronization, injected I/O failure, physical-device recovery/reboot/notification/upgrade QA and coordinated integration. PR #39 remains draft/unmerged; no release. P1 implementation not started.

### P0.6 full-data reset synchronization — 2026-11-27
- [x] Reset now shares workout commit mutex, awaits startup restoration, rejects an overlapping startup and prevents new starts while reset is executing. Cancels pending draft writer, clears workout/receipt journal before the remaining reset action, clears in-memory recovery state and invalidates pre-reset start requests and stale completion receipts via reset generation. Settings no longer launches independent discard; reset failure remains visible. Cancellation releases VM guards. Room schema unchanged.
- [x] Added regression cases for reset exclusion, stale start/write rejection, clearing recovery journal, injected reset-action failure and releasing gate. No assertions removed.
- [ ] Exact head `5c35028babe47df886b7cb5d23e1df11b2af12ee` CI: Palette regression check 45956504784 in_progress; Apex Fit Android CI & APK Build 45956504806 in_progress. Prior head `e2d9c72fb0e76fd75ecc15ecd822da1bf99f980b` remains verified 413/413. New tests and changes must pass before advancing. Reset is not a cross-store transaction: if remaining reset action fails after recovery data is cleared, it reports failure and does not resurrect intentionally discarded draft. Other non-workout writers and physical-device/injected storage-write QA remain outstanding. No merge; P1 not started.
