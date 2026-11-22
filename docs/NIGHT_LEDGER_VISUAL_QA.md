# APEX FIT Night Ledger — Visual QA Gate

Current continuation target: PR #33 on `chatgpt/functional-integration-safe-20261115`.

This file is the current merge gate. Older findings are retained only when they remain open.

## Status

**KEEP PR #33 DRAFT — do not merge to main yet.**

Automated Android CI, lint, unit tests, palette checks and Compose preview rendering are necessary but do not close this gate by themselves. Final completion still requires the preserved local Codex work, real Android validation where available, and Stitch comparison for the approved references.

## Closed since the first visual audit

- Adult-only launch boundary is now enforced at 18–100 in onboarding/profile/settings validation with tests.
- Onboarding Frequency uses the Night Ledger mineral-blue selected treatment.
- Ready now derives every session's exercise prescriptions from the same starter-plan logic used at save time. Day rows expose exercise and working-set counts; tapping a day reveals each exercise's sets, rep range, and rest. The bottom action remains visible while the preview scrolls.
- Muscle Volume now opens on the anatomy side containing the dominant direct-set work when appropriate.
- Form Guide no longer invents generic biomechanics when vetted written instructions are absent.
- User-facing recovery/history dates were moved toward localized display instead of raw ISO where migrated.
- Workout Complete shows actual elapsed and rest time plus per-exercise sets, loads, and reps; it omits aggregate volume per the user's request.
- Readiness confidence/copy was tightened and the baseline-building suppression remains.
- Nutrition has populated/no-target visual coverage.
- Progress has insufficient-data and populated dated-weight-trend visual coverage.
- Measurements summary was flattened and has empty/populated visual coverage.
- Exercise Picker now has loading, error, empty and no-match render coverage.
- Exercise-media loading/no-video/offline states are covered; their fixtures now render on the Night Ledger canvas rather than a transparent white preview.
- The technique-video surface no longer uses the old amber technical label or a mixed Material play icon.
- Workout Complete now has earned-PR, no-PR and partial-receipt render coverage.
- Train Program now renders a full seven-day schedule with an expanded selected session, real plan fields, substitution/Form Guide actions, and a start action. Its tabs and density were compared with the approved Stitch reference and refined.
- Plans now uses the flat active-plan summary, upcoming scheduled session, other-plan rows, import action and fixed Create plan action. Populated, empty, loading, active-plan unavailable and schedule unavailable states are rendered.
- Settings selection/focus treatment is being normalized away from generic amber; technical BMR-offset wording was removed.
- Progress weight logging copy now uses consumer-facing `Log weight` / `Weight history` wording and localized dates.
- The Ready, Form Guide, anatomy accessibility, and Plan Builder Night Ledger changes were published to PR #33 head `429394909b0d5ebe6e8065a362d3b8d1825287f1`; Android CI/APK Build and palette regression passed there. The Plan Builder numeric-range follow-up passes locally and requires CI on its next PR head.
- Plan Builder exercise input validation now rejects malformed numbers instead of silently replacing them with defaults. Kilogram/pound normalization and field-specific error routing are covered by `PlanExerciseDraftValidatorTest`; save and edit interaction still require a fresh UI render/device flow.
- The shared anatomy now exposes a screen-reader custom action for each visible muscle path; physical TalkBack verification remains open.

## Still blocking merge

### 1. Finish the reachable Night Ledger migration

The remaining high-risk legacy surfaces require the local/visual pass:
- Today: scheduled, rest-day, active-session, readiness explanation, and larger-font states have Compose renders; finish real-device inset/back checks and review remaining Stitch mismatches.
- active Workout and RIR/rest states have Compose renders; finish real-device interaction and motion checks.
- Plan Builder now has metadata, validation, save-action, add/edit training-day, and actual Add/Edit exercise dialog renders, including invalid prescription values and a 1.3× font state. Full Plan Builder persistence still needs device verification.
- Settings row/editor renders exist; finish reachable-flow and accessibility review.
- Exercise Detail/Form Guide now shows the real program prescription/progression, instructions, PR/history, and local-catalog same-primary-muscle alternatives. Program-screen alternatives now expose a Replace action that enters the existing substitution confirmation flow. Keep the missing photo pair and unsupported common-mistake coaching absent; verify the replace/save interaction, lower-section parity, and real-device entry/back behavior.
- Ready now shows the exact saved-plan prescription data behind expandable week rows, with starting calorie/protein estimates and a pinned Go to Today action. Fresh collapsed and expanded Compose renders were inspected; real-device scroll and action behavior remain open.
- Progress now surfaces real recent strength records, goal-aware weigh-in rate/chart states, weekly target markers, and per-muscle recovery estimates. The main overview was compared against its Stitch reference; finish real navigation and Stitch comparisons for the remaining detail routes where a reference exists.
- importer review/error states now have Compose renders; the import-to-save flow still needs verification.

A polished overview does not count if a tap drops into an older visual language.

### 2. One icon family

The current tree uses 27 curated Tabler outline vectors for app UI glyphs, with the selected SVG-derived vectors, MIT license notices, and provenance documented. The full icon-pack dependency is removed. APEX brand marks and the notification icon remain purpose-built. Bottom-nav labels stay visible. This is published in PR #33; device accessibility/tap-target validation remains open.

### 3. Muscle anatomy quality

The low-detail custom canvas was replaced with the selected MIT-licensed Jsplice/MuscleMap traced front/back vectors; the complete MIT notice and provenance are bundled and documented in `docs/THIRD_PARTY_ASSETS.md`. The bundled vectors load from cache before first draw. In the latest visual pass, the shared anatomy width increased from 240dp to 280dp in Exercise Picker and Muscle Volume to make muscle selection easier. On PR head `f0fc664b4aac0f40354c48c82896e1449b18fce6`, I inspected the picker and populated Muscle Volume renders from artifact `14566470969`; muscle outlines and selected/direct-set emphasis are visible at the larger size. No dedicated Stitch anatomy reference exists, so visual review used the Night Ledger palette, restraint, and set-count-only rules. Device tap-target and accessibility validation remains open.

### 4. Exercise Detail final polish

Preserve vetted source instructions and honest no-instruction fallback. The latest artifact covers the available-instructions, no-instructions, video-loading, no-video, available-video, and offline-video states. A new 1.3x-font render uses the bundled lateral-raise instruction text; I inspected the full-width wrapping and found no horizontal clipping. Program alternatives now route to the existing replacement confirmation. Still verify the active-workout Form Guide entry point and real-device scrolling/accessibility.

Do not invent coaching or biomechanics merely to fill empty space.

### 5. Visual-state coverage still missing

Latest Compose renders now cover the active Workout logger and adaptive set-volume row; Plan Builder metadata, validation, save action, and add/edit training-day dialogs; Settings row/editor; Personal Records populated/empty; Workout History list/detail; measurement empty/populated; Fatigue & Deload states; Muscle Volume week/month, selected muscle, loading/error and no-training; Workout Complete earned/no-record/partial; RIR selection/history/rest/pre-log states; and workout-import review/error states. The import review/error renders were visually inspected locally.

Remaining render/flow gaps include Workout Complete expanded details if that interaction exists in the live flow. The import-to-save and full Plan Builder ViewModel persistence flows remain unverified. Robolectric Compose interaction tests now verify the shared editor opens the exercise picker, returns with the selected exercise while preserving the prescription, and retains an existing prescription on edit/save. Screenshot coverage is still not a substitute for interactive device validation.

### 6. Real Android validation

Compose screenshot tests are preview renders. They are useful regression evidence but are **not** a substitute for:
- Android system bars/insets
- predictive/system Back
- IME/keyboard
- 1.3x font scale
- compact phone layout
- 48dp touch targets
- reduced/disabled animation behavior
- hardware/emulator interaction and performance

Use a real emulator/device when available. If the local Windows environment continues to fail, report that as a blocker instead of claiming completion.

### 7. Stitch fidelity

Stitch project: `14369859982852578965`.

Stitch MCP is responding: `list_screens` returned the approved screens and `download_file` retrieved the RIR/rest and Today references. The RIR/rest render from `c8ba042` and Today render from `ecb9f36` were compared against their references. Continue comparisons for the approved references that are actually available; never expose or request API keys.

### 8. Eight end-to-end flows

Run and report, where the environment permits, the eight flows defined in `docs/NIGHT_LEDGER_CODEX_REMAINDER.md`. Automated compilation alone is not an E2E pass. If hardware/emulator limitations block a flow, name the exact blocked step.

## Current automated evidence

- Current unpushed local Today pass is based on `be71435`. The latest isolated-source run passed `lintDebug`, `assembleDebug`, and `updateDebugScreenshotTest` (`BUILD SUCCESSFUL`, 5m 10s). The source snapshot hash matched the working tree. I inspected the fresh 390dp scheduled-pull render against the approved Stitch Today screenshot (`4451767822246429594`): the supported-row photo/crop, typography, workout hierarchy, two recent-session rows, and fixed bottom navigation fit. Readiness is labeled as an estimate, and the weekly count uses real completed sessions. Lower-body scheduled, active-session at 1.3x font, recovery/no-history, and readiness-explanation renders were also inspected. The 1.3x view scrolls; its last visible recent row continues below the viewport as expected. Physical device/system-bar/back checks remain open.
- The Today screenshots are local Compose renders. Treat them as visual evidence for the captured source state, not as a device run.
- The icon migration replaced Material and legacy UI glyphs with 27 curated Tabler outline vectors and is included in PR #33. The full icon-pack dependency was removed; only the selected vectors are bundled. The APEX logo/chevron brand marks and notification icon remain custom. Today, Exercise Picker, Workout Complete, and Progress renders were checked; device accessibility and tap-target verification remain open.
- The latest isolated-source run for the combined unpushed local tree passed `testDebugUnitTest lintDebug assembleDebug updateDebugScreenshotTest` (`BUILD SUCCESSFUL`, 11m 13s): 44 suites, 339 tests, no failures/errors/skips, and 80 Compose screenshot renders. This run is local evidence, not evidence for the remote PR head.
- Plan Builder Add/Edit exercise dialogs, invalid-set and invalid-rep-range states, and the 1.3× font Edit state were rendered at 390dp and visually inspected. `PlanBuilderExerciseEditorTest` passed both picker-selection/prescription-retention and unchanged-edit/save cases on the current PR head. Full Plan Builder ViewModel persistence remains open for device QA.
- Exercise Detail now has a 1.3x-font long-instruction render using its bundled exercise instructions. The fresh render was visually inspected; wrapped text remains legible without horizontal clipping. The incremental screenshot task passed in 3m 28s and produced 75 Compose renders.
- The Exercise Detail render includes same-primary-muscle catalog alternatives. The source catalog does not provide separate common-mistake coaching or a verified Start/Finish image pair, so those are intentionally not fabricated. On the Program screen, each alternative now opens the existing replacement confirmation and shared picker flow; device interaction QA remains open.
- Ready collapsed and expanded renders were visually inspected at 390dp. The expanded row shows all prescribed exercises, set counts, rep ranges, and rest intervals from the actual starter-plan prescription function; the bottom CTA remains visible. The new states have no matching committed golden, while the existing Ready golden differs because the screen now exposes the fuller plan.
- Plan Builder's exercise editor fields use Night Ledger text, surface, outline, and action tokens. Numeric input no longer silently falls back when invalid. The production dialog is shared with the screenshot fixtures; its invalid states and large-font layout render without clipping. Picker selection and edit/save retention have passing Robolectric Compose interaction tests; device-level ViewModel persistence remains open.
- Progress overview now uses the live records, weekly target, completed-session, and muscle-recovery models. Its insufficient-weigh-in render was visually compared with Stitch: the 2-entry weekly rate is shown, the chart remains withheld until three distinct dated entries, and the strength/consistency/recovery/Explore sections follow the approved flat hierarchy. A populated 3-entry state also renders the real chart. The latest screenshot pass rendered successfully.
- Adaptive set volume now receives the live fatigue estimate and deload recommendation at session start. Elevated fatigue blocks set additions; an active deload recommendation suppresses conflicting per-exercise adjustments. The targeted regression tests and full Gradle verification pass; on-device active-workout/history validation remains open.
- `git diff --check` passes for the current working tree.

- PR head `70b16066d5862beb2424ee7ffd845afba3f05008` passed Android CI/APK Build and Compose screenshot rendering (run `45064765961`), and palette regression (run `45064765890`). The run includes both Plan Builder exercise-editor interaction tests and all five exercise-editor render states.
- Current local changes passed `testDebugUnitTest lintDebug assembleDebug` (`BUILD SUCCESSFUL in 7m 47s`): 346 tests, 0 failures, 0 errors, 0 skipped; APK assembled and lint passed. The expanded Ready and replaceable-alternative Compose renders were generated and visually inspected. `validateDebugScreenshotTest` reports missing goldens for the new states and an intentional visual mismatch for the expanded Ready state; the user-owned `app/src/screenshotTestDebug/` baselines were left untouched.
- The latest full local check after the Plan Builder input-validation and anatomy screen-reader actions passed all 350 unit tests, `lintDebug`, and `assembleDebug`; the debug APK is present. Lint reports two existing `MissingPermission` warnings for exact rest-timer alarms. `validateDebugScreenshotTest` rendered 82 screens and failed 42 baseline comparisons (33 image mismatches, 9 missing goldens). Existing and user-owned screenshot references were not changed. The fresh Ready-expanded, Exercise Guide replacement, and Workout Complete receipt renders were visually inspected.
- Plan Builder draft validation now uses the same bounded input ranges as text import (sets 1–99, reps 1–999, rest 0–3600s) and displays field-specific range errors; the focused validator suite covers the limits.
- The latest full local check passed all 351 unit tests, `lintDebug`, and `assembleDebug` (`BUILD SUCCESSFUL` in 7m 3s); the debug APK is present. Lint reports two existing `MissingPermission` warnings for exact rest-timer alarms. Existing and user-owned screenshot references were not changed.
- A read-only ADB check found no attached device; this SDK has no installed system image or configured AVD. Device interaction, TalkBack, and system-bar verification therefore remain open.
- Compose render artifact `14566470969` contains 70 PNG states. The Exercise Picker and Muscle Volume anatomy renders were inspected after the 280dp sizing change; the adaptive-volume active-workout render was also inspected.
- Today was compared with Stitch screen `projects/14369859982852578965/screens/01d1c4d10ac44aa895349abb6604754f` (screenshot file `4451767822246429594`): local photo/crop and transition align; copy preserves the app's heuristic readiness wording and real preview data. Rest-day, active-session, and 1.3x-font states are present in the same exact-head artifact.
- Train Program was compared with Stitch screen `projects/14369859982852578965/screens/f72ba5e089ce4cf8a003e4c32c5671f4` (screenshot file `6540126667228233814`). The rendered weekly schedule now fits the full week at the preview viewport.
- Plans was compared with Stitch screen `projects/14369859982852578965/screens/aac06f19998b4540804c75afd71dd18f` (screenshot file `5916272567711751964`).
- The post-log RIR/rest screenshot was visually compared with the approved Stitch reference and refined for hierarchy, timer formatting, and real next-set context.
- Workout Complete was compared with Stitch screen `projects/14369859982852578965/screens/bf78310391cc4ca8a8237e5b7967cb4c`. The local receipt keeps the same flat headline / earned-PR / fixed actions hierarchy, adds actual duration and rest time, and shows each exercise's completed loads and reps. It omits the reference's aggregate load, consistent with the user's request for useful set detail without total volume.
- Exercise Detail was compared with Stitch screen `projects/14369859982852578965/screens/de4245a961994f0c8322ed0b89edd428`. The render includes the available prescription, progression target, PR/history, and catalog alternatives. It keeps vetted text and omits unverified imagery/coaching. The screen does not reproduce the sample's unsupported technique/mistake copy; the active-workout replacement flow still needs real-device verification.
- No Android AVD is configured in the local SDK. A physical-device run has not been performed in this pass; the evidence above is Compose screenshot rendering, not device validation.
- A transient Maven Central HTTP 429 on an intermediate run is infrastructure noise, not evidence of a source-code failure.

## Exit criteria

Close this gate only when:
- local `6423946` work is preserved/integrated;
- every reachable migrated surface uses the Night Ledger system;
- one icon family is consistent;
- relevant populated/empty/loading/error/offline states are rendered;
- the eight flows are run or exact environmental blockers are documented;
- real Android validation is completed wherever the environment permits;
- approved Stitch comparisons are completed where references exist;
- Android build, unit tests, lint, palette and screenshot rendering are green on the exact final PR head;
- PR #33 remains draft until all of the above evidence exists.

