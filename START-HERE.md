# Apex Fit development

Native Android application: Kotlin, Jetpack Compose, Hilt, Room.
Application ID: com.apexfit.app. Display name: Apex Fit.

## Onboarding
Welcome -> Losing / Recomp / Gaining -> Experience -> Frequency (2–5 days) -> Profile review.
Goal selection advances immediately. Back and Change retain selections. Explore opens the app without inventing profile inputs. Completion saves goal, experience and frequency; it does not generate a workout plan. Calorie and macro estimates require saved body details. Existing workout history and saved plans are preserved.

Reference assets and typography are bundled locally. Phone layouts use a compact single column; larger displays show a side photograph. Forms respect system bars and allow scrolling.

## Local checks
Use Java 21 and Android SDK 36, then run:
    ./gradlew :app:assembleDebug :app:testDebugUnitTest --tests '*OnboardingFlowTest' --tests '*OnboardingStateTest' --tests '*StarterPlanBuilderTest'

If necessary, override debug signing with -Papexfit.debugKeystore=<local-path>. Do not commit a signing key or machine-specific signing path.

No physical device is required for these tests. No phone installation is part of this change.
