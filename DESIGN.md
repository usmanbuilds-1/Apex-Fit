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
