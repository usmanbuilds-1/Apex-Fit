# Apex Fit — product interface direction

Native Kotlin / Jetpack Compose. Use the first onboarding screen as the color reference: charcoal-green canvas (#101617), layered surfaces (#1B2223 and #283031), warm off-white text, and the mint brand accent (#47E1C2).

## First-screen purpose
Today answers: What should I do next? Show the real scheduled workout and its primary action before secondary metrics. Keep photos and large editorial headings in onboarding, not above the daily task.

## Visual hierarchy
Use 24–28sp screen headings, 16–20sp section titles, and readable 13–16sp supporting text. Use existing font resources consistently. One strong mint primary action per screen. Quiet tonal surfaces; avoid nested cards, fixed-height labels, and tiny low-contrast controls. Phone dashboard summaries stack vertically; tablet layouts may use columns.

## Truthful states
Display actual logged values. Zero is zero; absent data has an explicit empty or loading state. Do not fabricate readiness, progression percentages, durations, sample workout history, weight trends, profile photographs, or calendar dates. Measurements stored in kg must be converted when labeled lb. Date labels must come from entries.

## Interaction
Every visible action must have a destination. Keep targets at least 48dp. Respect system bars, large fonts, and the keyboard. Workout logging and rest timers take priority over decorative motion. Preserve existing training history and onboarding persistence.

## Validation
Verify Today, Train, an active workout, Nutrition, and Progress on the Infinix HOT 40. Record screenshots and reproduce defects before expanding the redesign. Keep builds and device setup with Antigravity; report exact failures instead of broad dependency changes.
