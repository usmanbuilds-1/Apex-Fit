# Apex Fit development

Native Android application: Kotlin, Jetpack Compose, Hilt, Room.
Application ID: com.apexfit.app. Display name: Apex Fit.

## Onboarding
Welcome -> Losing / Recomp / Gaining -> Experience -> Frequency (2–5 days) -> Your details -> Profile review.
Your details collects name, height, weight, age, and sex used for energy estimates. Metric (cm/kg) and imperial (in/lb) inputs are converted to canonical cm/kg before saving. Invalid or missing details cannot complete setup. Back and Change retain answers, and the review lists personal details alongside training preferences.
Goal selection advances immediately. Explore opens the app without inventing profile inputs. Completion saves the supplied profile; it does not generate a workout plan. Calorie and macro estimates require saved body details. Existing workout history and saved plans are preserved.

Reference assets and typography are bundled locally. Phone layouts use a compact single column; larger displays show a side photograph. Forms respect system bars and allow scrolling.

## Local checks
Use Java 21 and Android SDK 36, then run:
    ./gradlew :app:assembleDebug :app:testDebugUnitTest --tests '*OnboardingFlowTest' --tests '*OnboardingStateTest' --tests '*StarterPlanBuilderTest'

If necessary, override debug signing with -Papexfit.debugKeystore=<local-path>. Do not commit a signing key or machine-specific signing path.

No physical device is required for these tests. No phone installation is part of this change.
