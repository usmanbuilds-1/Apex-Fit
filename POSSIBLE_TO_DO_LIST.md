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
| G01 | Review preview and technical plan; approve or revise scope | User | Technical plan ready; Today preview result unverified; awaiting review |
| G02 | Implement approved domain and UI work on coordinated branches | Both | Blocked by G01; UI also requires screen-specific approval |
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
