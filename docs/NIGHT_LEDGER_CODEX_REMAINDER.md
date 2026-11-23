# APEX FIT Night Ledger — Remaining Codex Work Ledger

This document is the version-controlled delta for the approved `APEX FIT — FINAL FUNCTIONAL INTEGRATION PASS`.

Baseline branch: `chatgpt/functional-integration-safe-20261115` (PR #33).

Status legend:
- **DONE** — implemented on PR #33; preserve and verify.
- **PARTIAL** — safe/domain work is done; Codex still owns the listed render/UI work.
- **CODEX** — requires Stitch fidelity, Compose UI work, emulator/device validation, or visual judgment.
- **VERIFY** — existing behavior should remain; do not rewrite without a demonstrated defect.

The original 0–50 acceptance criteria remain authoritative. This file prevents duplicated work.

All new screens/features are governed by `DESIGN.md`, including features added after this ledger was written. The Night Ledger design, truthfulness, accessibility, performance, navigation, motion, and validation rules are not optional per-feature styling suggestions.

## Pointer ledger

| # | Status | What is done / what remains |
|---|---|---|
| 0 | VERIFY | Keep exactly 4 bottom destinations: Today, Train, Nutrition, Progress. Train remains Program, Workout, Plans. No new top-level tabs. |
| 1 | PARTIAL | `DESIGN.md` now makes Night Ledger authoritative. **Codex:** migrate the actual Compose screens/tokens/fonts/icons to the approved Stitch references. |
| 2 | CODEX | Preserve approved screen priority/hierarchy during visual migration. Do not let new features overpower the primary task. |
| 3 | DONE | Free Exercise DB photos are disabled in production; false public-domain claims removed; instructions/videos preserved; regression coverage added. |
| 4 | PARTIAL/VERIFY | Form Guide now carries plan prescription, progression target, source instructions, real PR/history, and up to two same-primary-muscle alternatives from the local catalog. The Program route sends an alternative to the existing replacement confirmation; `TrainViewModelTest` verifies the transaction retains sets/reps and resets load/rest from replacement metadata. Compose checks cover entry, scroll, Back, and selection. Keep unverified photos and invented biomechanics out; physical-device replacement/save remains open. |
| 5 | DONE/VERIFY | `ExerciseMediaPanel` uses the DEMIC mapping + nocookie lazy playback from `exercise_videos.json` in both Exercise Detail and active-workout Form Guide. Compose checks verify playback starts only after a tap and the offline state offers retry; an offline fallback render is available. No standalone Videos area. Real Android playback, WebView network failure, and device Back behavior remain open because ADB cannot start and no AVD is installed. |
| 6 | DONE/VERIFY | Deterministic `SessionHeroResolver` is bound to `HomeViewModel.todayHeroCategory` and verified bundled assets. Upper-pull and quad sessions use contextual photos; unsupported splits use an honest no-photo fallback. Compose renders cover pull, lower, posterior, full-body, accessory, and generic-strength states. Physical plan/device flow remains open. |
| 7 | DONE/VERIFY | Today hero uses the approved supported-row photograph full bleed with the canvas fade. The corrected workout hierarchy was rendered and compared against Stitch. Crop and text contrast are acceptable in the deterministic render; device contrast QA remains open. |
| 8 | DONE | `docs/PHOTOGRAPHY.md` records all five bundled assets with original Pexels source URL/ID, photographer, license, use, download date, visible-mark notes, and verification status. Keep the documented crop limits. |
| 9 | PARTIAL | Active Today now opens real readiness factors and includes the non-medical estimate disclaimer. **Codex:** restyle to approved Night Ledger sheet and prioritize today's relevant factors visually. |
| 10 | VERIFY/PARTIAL | Existing readiness engine uses cautious recommendations. **Codex:** surface actionable calm wording only; no motivational filler. |
| 11 | DONE/VERIFY | Existing deterministic workout-duration estimator preserved; active Today now labels duration approximately. Do not rewrite engine without evidence. |
| 12 | DONE/PARTIAL | Active Today now explains what duration uses and what it does not model. **Codex:** restyle interaction to approved UI. |
| 13 | DONE | Plans exposes Import from text and reuses existing importer. |
| 14 | DONE | Plan Builder/direct Plans routes reuse one importer/review/save pipeline; direct cancel returns to Plans. |
| 15 | DONE | Imported plans inherit user's current goal, allow review/edit, and no longer silently hardcode General Fitness. |
| 16 | DONE/VERIFY | Train → Program exposes Exercise Library as a secondary route; no new tab. The screen is included in the Android render suite. Verify navigation on device. |
| 17 | DONE/VERIFY | One reusable Exercise Picker serves the Train library, Plan Builder Add Exercise, and Swap/Substitution flows. Search, Muscles, loading, error, empty, and no-match renders pass. Verify the shared routes on device. |
| 18 | DONE/VERIFY | `ExerciseCatalogFilter` ranks primary-muscle matches first; equipment and optional secondary exposure are visible in the picker. Domain tests and rendered Search/Muscles states cover the behavior. |
| 19 | DONE/VERIFY | Original front/back APEX anatomy uses the bundled MIT-licensed flat-vector paths with provenance. Both views render and expose named screen-reader selection actions. Verify physical taps and TalkBack on device. |
| 20 | DONE/VERIFY | Muscle Explorer selects a muscle and filters the exercise list without decorative heatmaps or cards. The responsive map was enlarged to 320dp maximum and its rendered size reviewed. Physical touch-target/TalkBack QA remains open. The separate Progress Muscle Volume view uses restrained direct-set intensity per `DESIGN.md`. |
| 21 | PARTIAL | Repo already has five onboarding stages; preference storage remains because other app code uses it. **Codex:** migrate visible onboarding order/design to Goal → Personal Details → Experience → Frequency → Ready. No mandatory Nutrition Preferences stage. |
| 22 | PARTIAL/VERIFY | Ready now derives each session's exercise list, sets, rep range, and rest from the same prescription logic used to create the saved plan. Day rows summarize the full week and expand to all exercise prescriptions; starting calories/protein remain computed from the profile. The CTA is pinned while the preview scrolls. Fresh collapsed/expanded Compose renders were inspected; compare with an approved Ready reference if one is added and verify on device. No photo/confetti/marketing graphic. |
| 23 | DONE/VERIFY | Flat Workout Complete receipt shows elapsed session time, completed working-set count, actual total rest, earned PRs, and each exercise's set loads/reps. Reps compress only for a consistent load; changed loads remain per-set. Rest uses monotonic elapsed time committed to the preceding completed set, including early skip/adjustment. No aggregate volume load is presented. Three receipt states rendered and visually reviewed against Stitch. The legacy local screenshot references differ by design; real-device navigation QA remains open. |
| 24 | DONE/VERIFY | Earned PRs use one brief confirm haptic and a 180 ms reveal only. No confetti/trophy/fake celebration, and no duplicate session-feel input. Real-device haptic QA remains open. |
| 25 | DONE/VERIFY | Progress Overview uses the flat Weight trend, Strength, Consistency, Recovery, and Explore hierarchy. Real data and the three-dated-weigh-in chart threshold render correctly; the Explore order now matches Stitch while retaining all destinations. Device navigation QA remains open. |
| 26 | DONE/VERIFY | Progress Explore preserves Personal Records, Workout History, Measurements, Training Volume, Muscle Recovery, and Fatigue & Deload. Each route has Compose render coverage; physical navigation QA remains open. |
| 27 | DONE/VERIFY | Personal Records shows actual records in restrained rows, with truthful empty coverage and no badge wall or fabricated praise. Compose renders pass; verify navigation on device. |
| 28 | DONE/VERIFY | Workout History preserves completion/partial state, duration, and drill-down set/load/reps/RPE details. Populated, empty, loading, and no-set detail states render. Device navigation QA remains open. |
| 29 | DONE/VERIFY | Measurement and weight-log views preserve real history, validation, and honest empty states. Trend rendering remains gated on enough dated entries. Compose states render; verify input/navigation on device. |
| 30 | DONE/VERIFY | **Progress → Explore → Training volume** uses Week/Month calendar windows, original APEX front/back anatomy, direct completed-set intensity, accessible ranked rows, drill-down exercise counts, and separate secondary exposure. Empty/loading/error/week/month/selection renders pass; verify taps on device. |
| 31 | DONE/VERIFY | Muscle Recovery uses readable rows and an estimate disclaimer; no recovery/growth heatmap is implied. Training data is rendered and calculations remain unchanged. Device navigation QA remains open. |
| 32 | DONE/VERIFY | Fatigue & Deload keeps the existing engine and shows a calm status, recommendation, and recovery destination without gauges. Insufficient-history, baseline, and deload states render. Verify action flow on device. |
| 33 | DONE/VERIFY | Today and Progress withhold the weight chart until three distinct dated weigh-ins. Progress may summarize a real two-weigh-in weekly rate without drawing a chart; three or more dated entries produce the longitudinal chart. Keep goal-aware wording and verify live navigation. |
| 34 | DONE/VERIFY | Weight actions use **Log weight** in Today and Progress. Continue the copy audit on any new surfaces. |
| 35 | CODEX | Preserve progressive disclosure: primary value on current screen, deep history under Explore; do not show everything at once. |
| 36 | PARTIAL/VERIFY | Existing set logging, RIR, importer, and meal-delete flows provide core feedback/undo. Today and Progress weight logging now keeps the dialog open on persistence failure, displays the retry message, disables duplicate submission/dismissal while saving, and closes after success; Compose interaction tests and failure/saving renders were added. **Still audit:** immediate visible confirmation for the remaining migrated high-value actions and confirm via device when available. |
| 37 | CODEX | One restrained motion language: ~120–180ms routine, ~180–220ms earned confirmations. No random bounce/pulse/infinite motion. |
| 38 | DONE/VERIFY | Reachable app UI glyphs now use 27 curated Tabler outline vectors with visible bottom-nav labels; APEX logo/chevrons and notification marks remain purpose-built. The final motion/icon sweep replaced the remaining importer reorder/delete text glyphs and onboarding selection checkmark with accessible Tabler vectors. MIT notices and provenance are bundled. Device contrast/TalkBack verification remains open. |
| 39 | CODEX | Implement/verify honest empty/loading/error states across plans, workouts, weights, PRs, history, measurements, recovery, media, search, importer, offline video. Never fill with sample data. |
| 40 | PARTIAL | Removed fabricated Today recent-session metrics; readiness disclaimer added; notification copy made neutral. **Codex:** final user-facing copy audit across migrated screens. |
| 41 | PARTIAL | Removed streak-at-midnight pressure, fake-coach language, urgent plateau framing; Today uses rest-day-safe weekly session count. **Codex:** final whole-app dark-pattern audit. |
| 42 | PARTIAL/CODEX | Domain derivations for hero/filtering are hoisted out of UI. **Codex:** profile real migrated screens; avoid network hero images, eager WebViews, N+ queries, heavy recomposition/God composables; verify fast tab switching. |
| 43 | CODEX | Device/emulator accessibility pass: 48dp targets, TalkBack, large fonts, keyboard, contrast, system bars, phone layouts. Anatomy muscle regions now expose named screen-reader actions, and Search/filter remains a non-visual route; verify TalkBack and touch behavior on a device. |
| 44 | DONE | `DESIGN.md` is the single Night Ledger source of truth. |
| 45 | VERIFY | Continue implementation in small screen/feature slices; build/test/render after each major surface rather than one giant migration. |
| 46 | VERIFY | Preserve Room, DataStore, Hilt, repositories, importer, matcher, progression/readiness/fatigue/recovery/duration/nutrition/video/workout persistence unless a concrete bug is proven. |
| 47 | PARTIAL/CODEX | Unit/Compose interaction coverage passed for workout/receipt, manual exercise editing, import/parser/persistence, muscle-based hero resolution, exercise filtering, picker/substitution, Progress aggregation, and readiness/weight states; the current serial full unit run passes 363 tests. **Still required:** run all eight journeys interactively on Android. Sandboxed ADB cannot start; elevated read-only ADB starts but sees no device or wireless-debugging service. The SDK has no AVD/system image or `sdkmanager`; see `docs/NIGHT_LEDGER_VISUAL_QA.md` for the exact gate and environment results. |
| 48 | CODEX | Run final anti-AI visual audit against each changed screen and approved Stitch references. |
| 49 | SHARED | Final correctness gate: PR #33 covers safe/domain items; Codex must verify the full UI/device checklist and report unknown items as unresolved. |
| 50 | CODEX | Final report must include branch/PR, screens/flows/components, media, hero resolver, import/picker/videos/readiness/duration/Progress/onboarding states, performance, accessibility, tests, render comparisons, build, limitations. |
| 51 | DONE/VERIFY | Adaptive set volume is separate from load/rep progression and the Muscle Volume view. The rule gates on goal, distinct completed-session evidence, readiness, effort, load changes, elevated fatigue, and deload recommendations; it changes at most one set, exposes the reason, supports safe undo, and writes the accepted adjustment into session notes for history. Elevated fatigue blocks additions; an active deload recommendation suppresses conflicting per-exercise changes. Domain and Compose states are covered. Validate full active-workout/history flows on device. |
| 52 | DONE/VERIFY | Starter plans now allocate conservative weekly direct sets by primary muscle across the selected training frequency, goal, experience, and exercise role. Exercise-role rep ranges and set bounds are covered by tests for 2–6 day plans. Continue science review if new templates are added. |
| 53 | PARTIAL/VERIFY | Readiness is surfaced as an app estimate with factor/history confidence explanations; cold start suppresses the score and workload ratios are explicitly not treated as readiness/injury predictors. The 45/25/30 weights and decay remain product heuristics; no longitudinal calibration has been performed. Review assumptions and maintain false-precision tests without claiming clinical validation. |
| 54 | DONE/VERIFY | First exposure uses a user-adjustable load calibration prompt, exercise-specific planned rep ranges, and a 2–3 RIR target; bodyweight added load is kept separate. Starter templates assign role-specific reps, and calibration / unit display / transition to logged history are covered by tests. Verify an actual workout flow on device. |

## Important existing code Codex must reuse

- `SessionHeroResolver` / `SessionHeroCategory`
- `HomeViewModel.todayHeroCategory`
- `ExerciseCatalogFilter`
- `ExerciseMediaCatalog`
- `ExerciseMediaPanel`
- `FormGuideSheet`
- `WorkoutImportFlow`
- `WorkoutImportViewModel`
- existing readiness, duration, fatigue, recovery, progression, PR, nutrition, Room/DataStore/Hilt architecture

Do not reimplement these simply to fit a screenshot.

## Required Codex sequence

1. Start from PR #33 HEAD, not stale `main`.
2. Run baseline tests/build.
3. Set up a usable emulator/AVD if possible.
4. Migrate shared Night Ledger theme/tokens/fonts/icons.
5. Migrate Today and bind `todayHeroCategory` to verified local hero assets.
6. Build unified Exercise Picker + original Muscle Explorer UI on top of `ExerciseCatalogFilter`.
7. Migrate Exercise Detail/Form Guide/video surface.
8. Redesign Workout Complete.
9. Migrate five-stage onboarding + Ready payoff.
10. Migrate Progress overview and all six detail destinations.
11. Global empty/error/loading, motion, icon, performance and accessibility pass.
12. Run the eight end-to-end flows.
13. Compare every migrated Android render to its approved Stitch reference.
14. Run full CI and report unresolved items rather than calling them done.

## Definition of done

Compiling is necessary but insufficient. Completion requires:
- real Android flows,
- passing automated tests/build,
- truthful states,
- preserved business functionality,
- approved Night Ledger fidelity,
- emulator/device validation wherever the environment permits.
