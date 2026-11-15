# APEX FIT — Night Ledger product interface direction

This file is the authoritative design target for the native Kotlin / Jetpack Compose app. Older mint / Neo-Pulse guidance is obsolete and must not be treated as current UI direction.

## Applicability

These rules apply to **every shipping screen, component, state, and new feature**, not only the screens included in the current redesign. A new capability does not get a separate visual language.

When adding or changing a feature:
- start from the product hierarchy and navigation rules in this file;
- reuse the Night Ledger tokens, typography, spacing, icon, motion, accessibility, truthful-state, and performance rules;
- preserve real domain behavior rather than designing around mock data;
- do not create a new top-level destination when the feature belongs under an existing route;
- render and verify the real Android implementation before considering the feature complete.

If a feature-specific spec conflicts with this file, **this file wins unless the conflict is explicitly resolved in a later committed design decision**.

## Product flow

Bottom navigation stays exactly:

- Today
- Train
- Nutrition
- Progress

Train stays:

- Program
- Workout
- Plans

Settings, Plan Builder, Exercise Detail, exercise discovery, Personal Records, Workout History, Measurements, Muscle Recovery, and Fatigue & Deload are secondary routes. Do not add another top-level tab just to surface a feature.

## Visual system

Night Ledger is dark, restrained, athletic, and tool-first.

- Canvas: `#101313`
- Primary surface: `#151918`
- Raised surface: `#1B201E`
- Interactive surface: `#202624`
- Highest surface: `#272E2B`
- Divider: `#2B3330`
- Strong divider: `#38423E`
- Primary text: `#F1F3F0`
- Secondary text: `#ADB6B0`
- Tertiary text: `#7E8983`
- Disabled text: `#626B66`
- Primary mineral blue: `#7192D3`
- Pressed blue: `#5B7EC3`
- Subtle blue surface: `#1A2638`
- Success: `#64A57D`
- Warning: `#C99B5B`
- Error: `#D46D68`

Use Archivo as the product type family when the bundled font migration is complete. Use tabular numerals for weights, reps, timers, calories, and other aligned numeric data.

## Hierarchy

Rank the user's task before styling the screen.

Today:
1. today's scheduled session
2. readiness interpretation
3. Start / Resume workout
4. nutrition
5. weight
6. recent training

Active Workout:
1. current exercise
2. current set
3. previous performance
4. weight / reps
5. Log set
6. Training Rail

Progress:
1. meaningful current trends
2. strength
3. consistency
4. recovery
5. deeper history / load tools

Use typography, spacing, alignment, position, and contrast before introducing a container. Cards are not the default primitive.

## Photography

Welcome and Today may use real human photography where it adds orientation or emotion.

Today photography:
- uses only locally bundled assets with documented commercial-use provenance;
- is resolved from the actual scheduled exercise/muscle focus, not the plan name;
- is full bleed and fades into the canvas rather than sitting inside a card;
- may be omitted when no honest contextual match exists.

Do not fetch random hero photography at runtime. Do not use exercise-reference images whose rights are unclear.

Instructional exercise imagery is separate from editorial hero photography. When a verified START / FINISH pair does not exist, show written technique guidance instead of synthetic or uncertain-rights imagery.

## Anti-AI / anti-slop rules

Do not introduce:
- electric mint identity
- neon or ambient glow
- glassmorphism
- gradient cards or gradient text
- nested cards
- bento dashboard grids
- giant rings or decorative radar charts
- icon pods
- pill / badge soup
- floating glass navigation
- fake analytics or sample data
- motivational SaaS copy
- AI-generated exercise-form anatomy

Do not solve empty space with decoration. Do not create a new screen, card, or tab simply because a new capability exists.

## Training prescription science boundaries

### Initial training volume

BMR/TDEE are **energy-expenditure tools** and must never determine resistance-training set volume. They may inform calorie targets only.

Starter training volume must be prescribed from training variables: goal, training experience, selected weekly frequency, exercise selection, and recoverability. The implementation should reason in **weekly completed working sets per primary muscle**, not apply the same set count blindly to every exercise.

Rules:
- start conservatively enough that the user can establish technique and a performance baseline;
- distribute weekly volume across the selected training days rather than forcing a per-exercise constant;
- do not claim one universal weekly set target is optimal for every user or muscle;
- use adaptive progression to increase/hold/reduce volume from observed performance/recovery rather than starting near an assumed maximum;
- indirect/secondary work may inform programming decisions, but user-facing direct-set totals remain separate and must not be presented as precise biological equivalents;
- BMR, calorie target, body fat estimates, or body weight alone must not raise/lower prescribed set counts.

### Readiness

The readiness feature is an **app-specific training-readiness estimate**, not a validated physiological measurement, medical assessment, CNS score, or injury predictor.

The current model may use recent completed training dose, exercise/muscle history, individualized historical capacity, and nutrition adherence as signals, but:
- decay constants, component weights, and thresholds are model assumptions unless individually calibrated;
- no single 0–100 value should imply laboratory precision;
- insufficient history must show a baseline-building state rather than a score;
- readiness must not override symptoms, pain, illness, or the user's own perceived condition;
- workload ratios must not be used as injury-risk or readiness predictors;
- when the UI shows a score, it must expose confidence and the factors that drove it;
- recommendations should be conservative choices (train as planned / consider reducing effort / consider recovery), not deterministic medical instructions.

Where possible, future calibration should compare the estimate with the user's subsequent workout performance and logged effort so recommendations become more individualized without pretending the model is clinically validated.

## Adaptive progression

Apex Fit has two distinct progression concepts and the UI must not blur them:

1. **Load / rep progression** — the existing `ProgressionEngine` remains authoritative for next-session weight suggestions. A clean success requires the prescribed working sets to reach the top of the rep range at RPE ≤ 8. RPE 9 is not a clean progression success. Stalled work holds load; repeated failed sessions may trigger the existing deload behavior.
2. **Set-volume progression** — a separate adaptive layer may change the number of prescribed working sets. It must not be inferred from the Muscle Volume heatmap itself.

Set-volume progression requirements:
- make no change from a single good or bad session;
- require repeated evidence across distinct completed exposures;
- be goal-aware: do not automatically increase hypertrophy-style set volume for goals where that is not appropriate;
- do not increase load and set count for the same exercise in the same progression step; change one training variable at a time;
- increase conservatively, at most **+1 prescribed working set for a muscle in a progression step**;
- reduce/hold volume when recovery/fatigue/deload signals argue against adding work;
- never treat the app's heuristic “effective set” score as a biological percentage or as sufficient evidence by itself;
- never count warm-ups as adaptive volume;
- preserve the user's original/base program and store adaptive changes as explicit next-session/program overrides rather than silently rewriting history;
- every automatic set-count change must be visible, explainable, and undoable before or during the affected workout;
- insufficient evidence means **hold**, not guess;
- tests must cover success, hold, fatigue/recovery block, deload/reduction, sparse history, duplicate-day evidence, goal gating, and undo/revert behavior.

Presentation:
- show the adjusted target next to the affected exercise using restrained copy such as `3 → 4 sets`;
- expose a short “Why?” explanation using real inputs (recent completed exposures, effort, recovery/fatigue state), not motivational copy;
- do not create a new top-level progression dashboard;
- deeper progression history belongs under Progress/Explore or exercise history, while the active workout shows only the adjustment relevant to today's work.

## Muscle volume

Muscle Volume lives under **Progress → Explore → Volume**. It is a training-log view, not a recovery, hypertrophy, or injury-prediction view.

The primary metric is **completed direct working sets**:
- count only completed, non-warm-up sets;
- attribute a direct set to the exercise's canonical primary muscle;
- do not convert secondary-muscle involvement into arbitrary fractional “effective sets”;
- secondary-muscle involvement may be shown separately as **secondary exposure**, but it must not be added to the direct-set total unless a future validated model explicitly defines that behavior;
- bodyweight work counts even when external load is zero.

Presentation:
- offer **Week / Month** time ranges using exact calendar windows;
- default to a calm front/back anatomy view using the original APEX anatomy asset;
- anatomy intensity represents **direct set count only**;
- use a small number of discrete Night Ledger intensity steps, not neon/thermal rainbow gradients;
- always provide an accessible ranked-list alternative with exact numbers;
- tapping a muscle opens a drill-down showing direct sets, secondary exposure, contributing exercises, and their set counts;
- when useful, show change versus the previous equivalent calendar period, but do not imply causality or “optimal” volume;
- empty states must say there is insufficient/no logged training data rather than painting inactive muscles as meaningful zero-performance signals.

Do **not** label the anatomy as recovery, growth, activation, soreness, or muscle quality. Recovery remains a separate Progress destination and must use its own explanation and data.

## Truthful states

Display real logged/computed values. Zero is zero. Missing data gets an explicit empty/loading/error state.

Never fabricate:
- readiness
- PRs
- weight trends
- workout history
- session duration
- recovery certainty
- chart points
- social proof

Algorithmic readiness/recovery/fatigue values are decision aids, not medical measurements. Explain them honestly when the user asks for detail.

Estimated workout duration must be marked approximate (for example `~54 min`) because it is derived from programmed sets, rep targets, tempo assumptions, and rest intervals.

## Interaction

Every visible action must have a real destination.

- Minimum touch target: 48dp.
- Labels remain visible in bottom navigation.
- Selected navigation uses more than color alone where the icon system supports a filled/stronger state.
- High-value actions give immediate feedback.
- Forms respect system bars, IME/keyboard, and large fonts.
- Workout logging and rest timing outrank decorative motion.
- Normal state changes should be restrained; motion must never hide latency.

## Performance

Performance is part of the design.

Avoid:
- network-dependent hero images
- oversized bitmap decoding
- WebViews before the user requests a video
- N+ database queries
- heavy work during recomposition
- God composables
- duplicate Flow collection where a shared state already exists

Preserve the direct screen host / tab-state behavior that was introduced to avoid invisible/black navigation layers.

## Existing functionality must survive visual migration

Do not drop working capabilities just because a Stitch overview screen does not display their full detail state.

Preserve:
- text workout import
- plan builder
- exercise matching
- technique video mappings
- progression / readiness / fatigue / recovery engines
- PRs
- workout history
- body measurements
- training volume
- deload guidance
- nutrition logging
- CSV export

## Validation

For each migrated screen:
1. build and run it;
2. capture the real Android render;
3. compare hierarchy and behavior against the approved Stitch reference;
4. verify real-data, empty, loading, and error states;
5. fix regressions before migrating the next surface.

Do not claim the redesign is complete because a mockup looks correct. The shipping Android flow is the product.
