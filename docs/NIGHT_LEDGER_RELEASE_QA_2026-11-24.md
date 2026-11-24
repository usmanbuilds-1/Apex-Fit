# APEX FIT Night Ledger release QA — 2026-11-24

## Revision and scope

- PR #33 head `c1b40018686b074dc4f7339469c3909b5600d665` was merged as `e0b045c17409acbdf07ce1cfaef634bd3d3b51c6`.
- The isolated QA checkout is based on that merged main commit. Its tree hash is `d6e6c7bee7bbe4ae5da6760117411b8e12f64c0e`, identical to the PR head's tree.
- No production UI, business logic, data, or assets were changed in this pass. The code changes add interaction evidence for Today explanation dismissal and the workout receipt's details/back/done flow.
- The original worktree and its user-owned screenshot references and `outputs/` were preserved. A separate copy of the 74 references was used here; neither reference set was updated.

## Visual review

The exact-tree Compose render artifact from [Android CI run 45320383411](https://github.com/usmanbuilds-1/Apex-Fit/actions/runs/45320383411) matches merge commit `e0b045c17409acbdf07ce1cfaef634bd3d3b51c6` and completed successfully. The render artifact is [ApexFit-Compose-renders.zip, artifact 14888145605](https://github.com/usmanbuilds-1/Apex-Fit/actions/runs/45320383411). This review also used local renders and their reference/diff contact sheets.

- **Today:** inspected scheduled lower-body and pull states, accessory/full-body/posterior/generic-strength no-unrelated-photo states, rest-day/no-history state, active-session resume without a matching photo, readiness baseline/explanation, and duration explanation. Both explanation previews use `fontScale=1.3`; the sheets are bottom-aligned with a visible Close action, scrollable body, and bottom spacing after the last line. Hero selection/fallback remains tied to scheduled exercise/muscle data. No unrelated image, blank sheet, or visible clipping was found. The explanation sheets have explicit close/dismiss interaction coverage below.
- **Nutrition, Active Workout, Plan Builder, Settings:** current Compose comparisons against the approved Stitch references are recorded in the dated entries of `NIGHT_LEDGER_VISUAL_QA.md`. Source is unchanged from those exact-tree comparisons. Plan Builder includes Add/Edit exercise-editor and invalid set/range states. Settings' comparison is component based; live navigation remains on the device checklist.
- **Progress details:** inspected Personal Records, Workout History, Measurements, Training Volume/Muscle Volume, Muscle Recovery, and Fatigue/Deload renders. The approved Stitch project has an overview reference only; these detail routes were compared to `DESIGN.md`, not represented as dedicated Stitch comparisons. No broken or clipped content was found in the reviewed renders.
- **Workout Complete:** inspected `WorkoutReceiptExerciseDetails_ec9e36e1_0.png` full size. It shows the actual 38-minute session, six completed working sets, total recorded rest of 5:05, and each set's load, repetitions, RPE, and rest. The current sample's single earned PR remains one. The summary does not invent results or show aggregate volume.
- **Onboarding and Exercise Detail/Form Guide:** inspected Welcome-to-Ready renders, including expanded/loading/unavailable plan-preview states, plus long-instruction, offline-video, and replaceable-alternative renders. No Nutrition Preferences stage was added. Video playback and replacement confirmation remain device-only checks.

No demonstrated production defect was found in this render pass, so there was no UI correction to rerender.

## Screenshot baseline triage

Command: `:app:validateDebugScreenshotTest --max-workers=1 --no-daemon`.

It generated all **104** previews. Against the copied set of **74** older references, **30 matched, 42 differed, and 32 had no reference**. This validation is **not green**: the 42 image assertions and 32 missing references are still reported by the task. Successful render generation and Stitch comparison are separate evidence; neither converts the golden validator's nonzero result into a pass. The validator did not write or update references.

The HTML report is at `app/build/reports/screenshotTest/preview/debug/index.html`; rendered PNGs and pixel diffs are under `app/build/outputs/screenshotTest-results/preview/debug/{rendered,diffs}`. Contact sheets are in local ignored `outputs/qa-review/`. These build/contact-sheet outputs are local evidence and are not source-controlled.

### 42 image differences — disposition by shared cause

Each name below has a generated render and a pixel-diff image. Contact sheets were inspected. The reviewed differences correspond to the approved Night Ledger restyle, newer state/content, or the requested per-set workout receipt; no confirmed regression was found. **Disposition: intentional design/data delta; keep the old reference unchanged and do not update the golden in this pass.**

- Exercise Detail adds current program/history context: `ExerciseDetailWithProgramAndHistory_78a45cf8_0.png`.
- Exercise discovery uses the approved larger anatomy illustration and current offline-video fallback: `ExercisePickerMuscles_8a9e850a_0.png`, `ExercisePickerMusclesBack_8a9e850a_0.png`, `ExerciseVideoOffline_8a9e850a_0.png`.
- Muscle Volume uses the larger selectable anatomy and updated month/current data presentation; Muscle Recovery uses current Night Ledger list treatment: `MuscleVolumeMonthWithTraining_8a9e850a_0.png`, `MuscleVolumeWithTraining_8a9e850a_0.png`, `MuscleRecoveryWithTraining_ec9e36e1_0.png`.
- Nutrition shows revised Night Ledger macro and no-target treatments: `NutritionWithLoggedMacros_ec9e36e1_0.png`, `NutritionWithNoTarget_ec9e36e1_0.png`.
- Onboarding and Ready use the current five-stage visual treatment and generated prescription preview: `OnboardingDetails_ec9e36e1_0.png`, `OnboardingExperience_ec9e36e1_0.png`, `OnboardingFrequency_ec9e36e1_0.png`, `OnboardingGoal_ec9e36e1_0.png`, `StartingPlanReady_ec9e36e1_0.png`.
- Progress uses current dated-weight, personal-record, and Night Ledger detail states: `ProgressWithDatedWeightTrend_78a45cf8_0.png`, `ProgressWeightHistoryFlat_8a9e850a_0.png`, `PersonalRecordsEmpty_8a9e850a_0.png`, `PersonalRecordsWithHistory_8a9e850a_0.png`.
- Plan Builder validation/metadata fields and Plans empty/loading/unavailable/populated states use current editor and Night Ledger presentation: `PlanBuilderMetadataWithValues_8a9e850a_0.png`, `PlanBuilderNameValidationError_8a9e850a_0.png`, `PlansActivePlanUnavailable_f35f9a05_0.png`, `PlansEmptyState_f35f9a05_0.png`, `PlansLoading_f35f9a05_0.png`, `PlansNightLedger_f35f9a05_0.png`, `PlansScheduleUnavailable_f35f9a05_0.png`.
- Settings uses current profile rows and expanded-editor presentation: `SettingsExpandedEditorNightLedger_8a9e850a_0.png`, `SettingsProfileRowsNightLedger_8a9e850a_0.png`.
- Today shows current explanation/recovery/resume and scheduled workout states, with real data and matching-photo fallback rules: `TodayReadinessExplanation_6fa7343b_0.png`, `TodayRecoveryWithNoHistory_8a9e850a_0.png`, `TodayResumeWithoutMatchingPhoto_6fa7343b_0.png`, `TodayScheduledLower_8a9e850a_0.png`, `TodayScheduledPull_12489f4a_0.png`.
- Train schedule uses current Night Ledger schedule rows: `TrainProgramScheduleNightLedger_f35f9a05_0.png`.
- Active Workout exposes current first-exposure calibration and adaptive set-volume states; rest/RIR screens use current logging controls: `ActiveWorkoutFirstExposureCalibrationNightLedger_8a9e850a_0.png`, `ActiveWorkoutWithAdaptiveSetVolumeNightLedger_8a9e850a_0.png`, `RestTimerNightLedger_8a9e850a_0.png`, `RirAndRestNightLedger_8a9e850a_0.png`, `RirHistoryNightLedger_8a9e850a_0.png`, `RirSelectionNightLedger_8a9e850a_0.png`.
- Workout Complete reports the actual completed/partial session, earned records, elapsed/rest time, and per-set loads/reps rather than the obsolete summary: `PartialWorkoutReceipt_ec9e36e1_0.png`, `WorkoutReceiptWithAnEarnedRecord_ec9e36e1_0.png`, `WorkoutReceiptWithoutRecord_ec9e36e1_0.png`.

### 32 renders without an older reference — disposition

Every render below was present in the generated preview set and inspected in the missing-reference contact sheets. The render, not a baseline, is the available evidence. No blank or obviously clipped state was observed. **Disposition: missing baseline with inspected render; preserve the existing baseline set and do not create/update goldens in this pass.**

- Approved comparison previews: `PlanBuilderFullScreenNightLedger_9ef0561f_0.png`, `SettingsFullScreenNightLedger_37224418_0.png`.
- Exercise Detail/Form Guide: `ExerciseDetailLongInstructionsLargeFont_634de00b_0.png`, `ExerciseDetailOfflineVideoFallback_651a06a0_0.png`, `ExerciseGuideWithReplaceableAlternatives_8a9e850a_0.png`.
- Nutrition: `NutritionEmptyDayScreenLargeText_69705b54_0.png`, `NutritionLoggedDayScreen_ec9e36e1_0.png`.
- Progress insufficient data: `ProgressWithInsufficientWeighIns_fbedf78c_0.png`.
- Ready preview states: `StartingPlanReadyExpanded_ec9e36e1_0.png`, `StartingPlanReadyLoadingLibrary_ec9e36e1_0.png`, `StartingPlanReadyUnavailableLibrary_ec9e36e1_0.png`.
- Plan Builder dialog/editor states: `PlanBuilderAddDayDialogRender_8a9e850a_0.png`, `PlanBuilderAddExerciseDialogRender_8a9e850a_0.png`, `PlanBuilderDayDialogValidationErrorRender_8a9e850a_0.png`, `PlanBuilderEditDayDialogRender_8a9e850a_0.png`, `PlanBuilderEditExerciseDialogLargeFontRender_6fa7343b_0.png`, `PlanBuilderEditExerciseDialogRender_8a9e850a_0.png`, `PlanBuilderExerciseEditorInvalidRepRangeRender_8a9e850a_0.png`, `PlanBuilderExerciseEditorInvalidSetsRender_8a9e850a_0.png`.
- Today explanation and photo-selection states: `TodayReadinessBaselineExplanation_6fa7343b_0.png`, `TodayScheduledAccessoryWithoutUnrelatedPhoto_8a9e850a_0.png`, `TodayScheduledFullBodyWithoutUnrelatedPhoto_8a9e850a_0.png`, `TodayScheduledGenericStrengthWithoutUnrelatedPhoto_8a9e850a_0.png`, `TodayScheduledPosteriorWithoutUnrelatedPhoto_8a9e850a_0.png`, `TodayWorkoutDurationExplanation_6fa7343b_0.png`.
- Weight logging: `WeightLogSaveFailure_8a9e850a_0.png`, `WeightLogSaveFailureLargeFont_6fa7343b_0.png`, `WeightLogSaving_8a9e850a_0.png`.
- Active Workout: `ActiveWorkoutNightLedger_ec9e36e1_0.png`.
- Import review: `WorkoutImportReviewSaveError_8a9e850a_0.png`, `WorkoutImportReviewWithUnresolvedExercise_8a9e850a_0.png`.
- Receipt detail: `WorkoutReceiptExerciseDetails_ec9e36e1_0.png`.

## Interaction evidence added

- `TodayExplanationSheetInteractionTest.durationExplanationDismissesFromCloseAction` verifies that the duration sheet closes via its visible Close action. The existing readiness-explanation close test also runs in this suite.
- `SessionCompleteSheetInteractionTest.viewWorkoutOpensDetailsAndSystemBackReturnsToReceipt` verifies View workout opens per-set detail and Android Back returns to the receipt without dismissing it. `doneInvokesTheReceiptDismissAction` verifies Done invokes the receipt-dismiss callback from details.
- Existing `ExerciseDetailInteractionTest`, `PlanBuilderExerciseEditorTest`, `WeightLogDialogInteractionTest`, `WorkoutReceiptSummaryTest`, and domain tests passed in the full unit suite. These do not substitute for device playback, real navigation, or the end-to-end journeys below.

## Verification results

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --max-workers=1 --no-daemon` — **BUILD SUCCESSFUL** on this QA revision. No unit-test failure was reported. Debug APK assembled at `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:testDebugUnitTest --tests com.apexfit.app.ui.theme.ColorInitializationTest --max-workers=1 --no-daemon` — **BUILD SUCCESSFUL**; both palette initialization tests passed.
- Lint completed successfully with 142 warnings and 4 hints in the generated report; the two MissingPermission warnings are the exact-alarm checks in `RestTimerAlarmReceiver.kt`. Other warnings were not expanded into unrelated cleanup.
- `git diff --check` — passed.
- `:app:validateDebugScreenshotTest` — rendering succeeded, golden validation is **not green** (30 match, 42 intentional-difference dispositions, 32 missing-reference dispositions), as detailed above.
- [Merged-main Android CI run 45320383411](https://github.com/usmanbuilds-1/Apex-Fit/actions/runs/45320383411) completed successfully on `e0b045c17409acbdf07ce1cfaef634bd3d3b51c6`; its Compose render artifact is [14888145605](https://github.com/usmanbuilds-1/Apex-Fit/actions/runs/45320383411).
- The merged-main installable debug APK is available as [artifact 14887064032](https://github.com/usmanbuilds-1/Apex-Fit/actions/runs/45320383411/artifacts/14887064032) until 2026-12-08. This QA checkout also assembled `app/build/outputs/apk/debug/app-debug.apk`.

## Owner device checklist

No physical Android device or configured emulator is available here. These checks are pending and must not be counted as run.

1. **Workout receipt:** complete a workout with per-set rep changes and varied weights; adjust one rest interval, shorten another, and skip one. Expected: receipt lists each set in order with its own load/reps, elapsed session time is actual, and total rest includes only time actually taken.
2. **Manual plan:** create a plan, add/edit a day and exercise, save, leave, and reopen. Expected: chosen exercises and prescriptions persist; invalid name/range values stay blocked with visible errors.
3. **Text import:** import a plan with one unresolved exercise, review/edit it, then test Save and Cancel. Expected: unresolved items are explicit, save errors remain recoverable, and cancel persists nothing.
4. **Custom/Bro Split hero:** schedule days with different exercises/muscles and view Today. Expected: only a supported matching photo appears; unsupported or generic sessions show the honest no-photo fallback.
5. **Exercise discovery:** search, apply equipment, switch front/back muscle selection, and inspect results. Expected: filters combine correctly and primary-muscle matches precede secondary matches.
6. **Plan Builder picker reuse:** use Add Exercise and Swap/Substitute. Expected: both open the same library/muscle picker, preserve the selection, and save the requested prescription.
7. **Progress navigation:** open Personal Records, Workout History, Measurements, Training Volume, Muscle Recovery, and Fatigue & Deload, then use Back. Expected: all six destinations load real states and return to their parent correctly.
8. **Insufficient data:** view readiness without a baseline and weight progress with fewer than three dated weigh-ins, then add a third dated entry. Expected: readiness explains its baseline limits; no chart/fake values appear below threshold; a chart appears only with three dated entries.
9. **Cross-cutting:** open a Form Guide video online and offline, retry after reconnecting, confirm an alternative, and check system Back, keyboard/insets, large text, TalkBack labels/touch targets, reduced motion, PR haptics, and rapid tab changes. Expected: playback is user-started, errors/retry are clear, and navigation/state/accessibility remain usable.

## Final disposition

No production change was justified by render evidence. The two added interaction tests close observable dismissal/receipt-navigation evidence gaps. The automated local build and palette check pass. Screenshot golden validation remains non-green because the copied 74-reference set predates or omits the approved Night Ledger states; no golden was rewritten. The remaining blocker is owner-run Android device/emulator QA. PR #33 is merged; any follow-up review should remain draft.
