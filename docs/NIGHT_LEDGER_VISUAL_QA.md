# APEX FIT Night Ledger — Visual QA Gate

Current continuation target: PR #33 on `chatgpt/functional-integration-safe-20261115`.

This file is the current merge gate. Older findings are retained only when they remain open.

## Status

**KEEP PR #33 DRAFT — do not merge to main yet.**

Automated Android CI, lint, unit tests, palette checks and Compose preview rendering are necessary but do not close this gate by themselves. Final completion still requires the preserved local Codex work, real Android validation where available, and Stitch comparison for the approved references.

## Closed since the first visual audit

- Adult-only launch boundary is now enforced at 18–100 in onboarding/profile/settings validation with tests.
- Onboarding Frequency uses the Night Ledger mineral-blue selected treatment.
- Ready now identifies truncated exercise previews with `+N more` instead of silently presenting partial sessions as complete.
- Muscle Volume now opens on the anatomy side containing the dominant direct-set work when appropriate.
- Form Guide no longer invents generic biomechanics when vetted written instructions are absent.
- User-facing recovery/history dates were moved toward localized display instead of raw ISO where migrated.
- Workout Complete labels aggregate load as total load.
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
- Local branch `codex/night-ledger-pr33` is synced to PR #33; local changes were preserved and pushed through `c8ba042`.

## Still blocking merge

### 1. Finish the reachable Night Ledger migration

The remaining high-risk legacy surfaces require the local/visual pass:
- Today: finish Stitch comparison of scheduled, rest-day, insufficient-weight and larger-font states.
- active Workout
- Plan Builder and its edit dialogs
- Settings final polish
- Progress weight/body-metrics subroutes
- Personal Records / Workout History detail
- import review where reachable

A polished overview does not count if a tap drops into an older visual language.

### 2. One icon family

The app still mixes Material icons and legacy/custom vector assets on reachable screens. Finalize one approved family (Phosphor, HugeIcons, Radix or Tabler per the product direction), keep bottom-nav labels visible, and use a coherent selected/inactive treatment. Do not hand-roll replacement glyphs.

### 3. Muscle anatomy quality

The low-detail custom canvas was replaced with the selected MIT-licensed Jsplice/MuscleMap traced front/back vectors; the complete MIT notice and provenance are bundled and documented in `docs/THIRD_PARTY_ASSETS.md`. Exact-head screenshot review found that asynchronous loading captured only a spinner, so the bundled vectors now load from cache before first draw. On head `c3767593d298a6905efdd2716f3f815a43766818`, the front/back picker views and populated Muscle Volume render were inspected from artifact `14499404932`; the anatomy is visible, with mineral-blue selection and direct-set emphasis. No dedicated Stitch anatomy reference exists, so the comparison used the Night Ledger palette, restraint, and set-count-only rules. Device tap-target/accessibility validation remains in the final Android QA gate.

### 4. Exercise Detail final polish

Preserve vetted source instructions and honest no-instruction fallback. Final pass still needs to verify:
- long real instruction sets
- available-video state before playback
- no-video state
- offline/unavailable video state
- large-font behavior
- active-workout Form Guide entry point

Do not invent coaching or biomechanics merely to fill empty space.

### 5. Visual-state coverage still missing

Add/verify real renders for:
- active Workout logger
- Plan Builder
- Settings
- Personal Records populated/empty
- Workout History list/detail
- body measurement detail
- Fatigue & Deload
- Muscle Volume selected drill-down / Month / loading or failure path as applicable
- Workout Complete expanded details
- importer review/error states
- combined RIR selection + active rest state, history, and pre-log state

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

- Latest confirmed green code head: `c3767593d298a6905efdd2716f3f815a43766818`.
- Android CI/APK build, unit tests, lint, and Compose screenshot rendering passed on that exact head (run `44462081742`); palette regression passed (run `44462081547`).
- Compose render artifact `14499404932` contains 47 PNG states, including the revised front/back anatomy and populated Muscle Volume views.
- Train Program was compared with Stitch screen `projects/14369859982852578965/screens/f72ba5e089ce4cf8a003e4c32c5671f4` (screenshot file `6540126667228233814`). The rendered weekly schedule now fits the full week at the preview viewport.
- Plans was compared with Stitch screen `projects/14369859982852578965/screens/aac06f19998b4540804c75afd71dd18f` (screenshot file `5916272567711751964`).
- Today was checked against Stitch: the contextual local photo and fade align; the main readiness row was shortened to remove duplicate explanation copy. Calorie and weight hierarchy is receiving one more compactness pass.
- The post-log RIR/rest screenshot was visually compared with the approved Stitch reference and refined for hierarchy, timer formatting, and real next-set context.
- Subsequent changes must be reverified on their exact final head.
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
