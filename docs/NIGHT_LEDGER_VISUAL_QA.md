# APEX FIT Night Ledger — Visual QA Gate

Audit target: PR #33, Android head `6cc59d981487eb3790e1b044f7fea44a07162242`.

Evidence reviewed:
- Android CI run `43780405932` — PASS.
- Palette regression run `43780405966` — PASS.
- Compose render artifact `14187459130`.
- All 18 rendered preview states in that artifact.
- Reachable Compose implementations changed by the Night Ledger migration.
- `DESIGN.md`, the Night Ledger work ledger, and the bundled Impeccable Android/craft-floor guidance.

## Verdict

**BLOCKED — keep PR #33 draft.**

The new direction is materially better and several surfaces are already strong, but the product is not yet consistently at the Night Ledger quality bar. The current screenshot workflow proves that selected Compose previews render; it does **not** prove full-screen visual fidelity to the approved Stitch references, complete state coverage, or real-device correctness.

Do not merge PR #33 until the Must Fix items below are closed and the final device/emulator pass is complete.

## Strong / preserve

- Welcome: full-bleed licensed photography, clear hierarchy, restrained CTA treatment.
- Onboarding Goal and Experience: flat hierarchy, low clutter, coherent selection treatment.
- Onboarding Details: clear fields and inline validation structure.
- Today recovery/no-history, resume/no-photo, lower and pull states: contextual hero/fallback behavior is honest and the primary action is clear.
- Progress insufficient-data state: truthful, no fake chart/data, flat Explore hierarchy.
- Workout receipt: flat receipt structure, no trophy/stat-wall treatment; earned PR reveal is restrained.
- Workout History structure: real sessions and set drill-down, honest empty/loading behavior.
- Photography provenance is documented in `docs/PHOTOGRAPHY.md`.

Preserve these qualities while fixing the remaining surfaces.

## Must Fix before merge

1. **Finish the Night Ledger migration on reachable legacy surfaces.**
   - Train Program still uses `PremiumCard`, legacy amber/technical styling and older hierarchy.
   - Plan Builder still relies on repeated rounded Cards, legacy aliases, and mixed Material icons.
   - Body Measurements detail/list still uses card-heavy legacy styling.
   - Deload presentation still uses the old rounded amber callout.
   - Audit Nutrition and Settings explicitly; they are not covered by the current render artifact.
   - A new overview is not enough if a tap leads into the old visual language.

2. **One icon family, everywhere.**
   - Bottom navigation currently mixes a custom outline Today calendar with filled legacy dumbbell/cutlery/trending vectors.
   - Settings, onboarding/back, Progress detail, Personal Records and Plan Builder still use Material icon sets.
   - Replace with one approved family and implement a coherent inactive/selected treatment. Labels remain visible. No icon pods.

3. **Muscle Volume: make the first frame useful.**
   - The current training fixture contains only Back direct work while the screen opens on the Front anatomy, so no active muscle is visible.
   - Default to the side containing the dominant active/selected muscle or otherwise make the active region obvious immediately.
   - Keep the ranked list and direct-set semantics.
   - The current polygon anatomy is functionally original and accessible, but visually too schematic for the final consumer-quality bar. Refine the original APEX anatomy without copying Lyfta or introducing glow/thermal styling.
   - Add selected-muscle drill-down, back view, Month, loading/error and empty renders.

4. **Ready screen must show the real first week.**
   - `SetupReady` truncates each session to `take(3)` exercises with no indication that more exercises exist.
   - Show the complete planned session or an honest compact summary such as the first three plus “+2 more”.
   - Do not describe a partial preview as the full first week.

5. **Exercise Detail / Form Guide content is not finished.**
   - The current no-image render is too thin and leaves large unused space.
   - The generic fallback copy in `FormGuideBuilder` (“Drive the movement motion deliberately…”) reads generated and is not useful coaching.
   - Structure usable guidance around setup / starting position / execution / breathing-bracing where appropriate / common mistakes, preferring vetted source instructions.
   - Do not invent biomechanics when source guidance is unavailable.
   - Render verified-video, no-video, offline/error and written-fallback states.

6. **Humanize developer-looking data presentation.**
   - Muscle Recovery, Personal Records and Workout History expose raw ISO dates in user-facing rows.
   - Localize dates consistently.
   - Workout receipt summary currently renders aggregate load as e.g. `6,840 kg` without naming the metric; label it as load/training volume so it cannot be mistaken for body weight.
   - Review copy such as “system local time auto picked” and other registry/debug-style language.

7. **Readiness UI must remain explicitly heuristic.**
   - Fix the confidence mapping in `UiModels.kt`: `dataConfidence == "full"` currently becomes “More logged history”, which is not a confidence label.
   - Avoid presenting internal model percentages as if physiologically measured.
   - Preserve the baseline-building suppression and the explanation that the weights/decay timing are assumptions.
   - The real runtime recommendation text is longer than the screenshot fixture’s “Normal session”; render with realistic copy and large font to verify hierarchy.

8. **Use tabular numerals consistently where the design contract requires them.**
   - `ApexTextStyles` correctly defines `tnum`, but not all weights/reps/timers/calories use it.
   - Apply data styles intentionally rather than hand-picking numeric font sizes screen by screen.

9. **Unify onboarding selected states.**
   - Goal/Experience use mineral-blue selection.
   - Frequency currently switches the selected day to near-white/black, which reads like a different product.
   - Bring it into the same Night Ledger interaction language while retaining strong contrast and non-color selection cues.

10. **Motion must match the Night Ledger duration contract.**
    - Earned PR reveal at 180 ms is correct.
    - Audit/remove any remaining 420 ms `ApexMotion.momentCoach()` usage unless a documented exception is justified.
    - Honor Android animation reduction/disable settings in the final device pass.

11. **Final screenshot/state coverage is incomplete.**
    Current render coverage is useful but not sufficient. Add visual coverage for:
    - Train Program / active Workout / Plans.
    - Plan Builder.
    - Nutrition.
    - Settings.
    - Personal Records populated/empty.
    - Workout History list/detail.
    - Measurements list/detail.
    - Fatigue & Deload.
    - Progress populated state with a real longitudinal weight trend.
    - Exercise Picker loading/error/no-results/filter states.
    - Muscle Volume selected/back/month/loading/error states.
    - Workout Complete no-PR, partial, and expanded-details states.
    - Form Guide video/no-video/offline states.
    - Meaningful validation/error/loading/empty states for onboarding and other changed forms.

12. **Compose Preview renders are not the final Android validation.**
    - The CI task runs `:app:updateDebugScreenshotTest`; it generates preview renders but does not compare them against approved Stitch references.
    - Complete a real emulator/device pass with system bars, predictive/system Back, keyboard/IME, 1.3x font scale, 48dp targets and compact-phone layouts.
    - Compare the real Android renders against the approved Stitch references. Report any reference that is unavailable instead of silently substituting taste.
    - Do not call the redesign complete based only on screenshot-test generation.

## Additional release correctness blocker

Onboarding still accepts age 13–17 while the app uses adult energy-estimation logic. Resolve the existing product decision (18+ launch vs a deliberately supported adolescent path) before release. This is not primarily a visual defect, but it remains a launch blocker.

## Render inventory reviewed

- Welcome
- Goal
- Personal Details
- Experience
- Frequency
- Ready
- Today: scheduled pull
- Today: scheduled lower
- Today: recovery/no history
- Today: resume/no matching photo
- Exercise Picker: search
- Exercise Picker: muscle filter
- Exercise Guide: no verified images
- Muscle Volume: training
- Muscle Volume: no training
- Muscle Recovery: training
- Progress: insufficient weigh-ins
- Workout Receipt: earned PR

## Exit criteria

This QA gate is closed only when:
- every reachable migrated surface uses the same Night Ledger visual system;
- the Must Fix items above are addressed or explicitly documented as an accepted exception;
- relevant populated, empty, loading, error/offline and insufficient-data states are rendered;
- one icon family is used consistently;
- the final real Android render/device pass succeeds;
- the approved Stitch references are compared where available;
- build, unit tests, lint, palette checks and screenshot checks are green;
- PR #33 remains draft until that evidence exists.
