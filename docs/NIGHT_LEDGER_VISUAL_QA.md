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
- Settings selection/focus treatment is being normalized away from generic amber; technical BMR-offset wording was removed.
- Progress weight logging copy now uses consumer-facing `Log weight` / `Weight history` wording and localized dates.

## Still blocking merge

### 1. Preserve and integrate local Codex work

Local branch `codex/night-ledger-pr33` contains commit `6423946`, which is not present on GitHub. Do not reset, overwrite or discard it. Before broad remote UI rewrites, sync the remote PR into that local branch semantically and preserve the local work.

### 2. Finish the reachable Night Ledger migration

The remaining high-risk legacy surfaces require the local/visual pass:
- Train Program / active Workout / Plans
- Plan Builder and its edit dialogs
- Settings final polish
- Progress weight/body-metrics subroutes
- Personal Records / Workout History detail
- rest timer / RPE sheets / import review where reachable

A polished overview does not count if a tap drops into an older visual language.

### 3. One icon family

The app still mixes Material icons and legacy/custom vector assets on reachable screens. Finalize one approved family (Phosphor, HugeIcons, Radix or Tabler per the product direction), keep bottom-nav labels visible, and use a coherent selected/inactive treatment. Do not hand-roll replacement glyphs.

### 4. Muscle anatomy quality

Muscle Volume behavior is improved, but the original APEX anatomy still needs final visual refinement against the Night Ledger quality bar. Keep it original, accessible, restrained and set-count-only; do not copy Lyfta or turn it into a neon/thermal recovery map.

### 5. Exercise Detail final polish

Preserve vetted source instructions and honest no-instruction fallback. Final pass still needs to verify:
- long real instruction sets
- available-video state before playback
- no-video state
- offline/unavailable video state
- large-font behavior
- active-workout Form Guide entry point

Do not invent coaching or biomechanics merely to fill empty space.

### 6. Visual-state coverage still missing

Add/verify real renders for:
- Train Program
- active Workout logger
- Plans
- Plan Builder
- Settings
- Personal Records populated/empty
- Workout History list/detail
- body measurement detail
- Fatigue & Deload
- Muscle Volume selected drill-down / Month / loading or failure path as applicable
- Workout Complete expanded details
- importer review/error states
- rest timer and RPE/RIR sheet

### 7. Real Android validation

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

### 8. Stitch fidelity

Stitch project: `14369859982852578965`.

The local Codex environment has a configured direct Stitch endpoint and has already retrieved/compared the approved Today reference. Do not expose or request the key. Continue comparisons for the approved references that are actually available. The connected Stitch tool authentication failure does not invalidate the working local direct endpoint.

### 9. Eight end-to-end flows

Run and report, where the environment permits, the eight flows defined in `docs/NIGHT_LEDGER_CODEX_REMAINDER.md`. Automated compilation alone is not an E2E pass. If hardware/emulator limitations block a flow, name the exact blocked step.

## Current automated evidence

- Exact PR head must have Android CI/APK + palette success before any final report.
- Previous confirmed green head: `a47677fbf53246e26d5e418857cb263bb17343b2`.
- That head produced the expanded Compose render artifact including onboarding, Today, Nutrition, Progress, Measurements, Exercise Picker/media, Muscle Volume, Recovery and Workout Receipt states.
- Subsequent isolated remote-safe UI/test commits must be reverified on their exact final head.
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
