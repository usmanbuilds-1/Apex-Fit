# ApexFit Complete UI/UX Overhaul

A comprehensive architectural and design overhaul transforming ApexFit into a high-fidelity, custom-designed strength and telemetry experience guided by user-provided design specs, screenshots, and visual assets.

### User Review & Critical Decisions

> [!IMPORTANT]
> The overhaul will be implemented directly against the design system, screenshots, and assets you provide. To execute the changes accurately, please provide the details below in subsequent prompts or uploads:
> 1. **Design System Tokens**: Color scheme (background, elevated surfaces, borders, text colors, status/accent accents) and shape/elevation tokens.
> 2. **Screenshots & Layout Wireframes**: Visuals of each target screen (Home / Dashboard, Active Workout Logger, Nutrition & Energy, Progress / 1RM, Settings).
> 3. **Asset Files**: Any SVG vectors, drawables, or font files you would like incorporated into `res/drawable` and `res/font`.
> 4. **Screen-by-Screen Changes**: Specific guidance on which existing components to replace, refactor, or reorganize.

- **Confirmed Scope**: Complete UI/UX overhaul across all primary surfaces and component libraries.
- **Confirmed Assets & Specs**: User has design system, screenshots, and assets ready to share.
- **Architecture Strategy**: Phased tokenization and screen migration ensuring zero regression to progression, fatigue, and workout calculation engines.

---

### 1. Overview & Core Concept

- **What It Does**: Modernizes the visual and interactive experience of ApexFit—unifying workout logging, real-time muscle fatigue telemetry, scientific starting weight calculations, nutrition tracking, and long-term progress into a cohesive, production-grade interface.
- **Target Audience / Persona**: Serious lifters, athletes, and fitness enthusiasts who value data density, precision tracking, and an inspiring, tactile athletic aesthetic.
- **Key Value**: Replaces legacy and experimental components with a unified design system, delivering frictionless workout logging and rapid visual comprehension of recovery and readiness.

---

### 2. User Experience & Visual Design

#### Visual Identity & Theme
- **Aesthetic Posture**: Custom athletic design system dictated by user-provided screenshots and design tokens.
- **Token Harmonization**:
  - `Color.kt`: Centralized color tokens (`Background`, `Surface`, `SurfaceElevated`, `BorderDefault`, `BorderSubtle`, `Primary`, `Accent`, `Warning`, `Error`, `TextPrimary`, `TextSecondary`).
  - `Type.kt`: Integrated custom typography pairings (Display headings + Body UI + JetBrains Mono numerical telemetry).
  - `Shape.kt` & `Spacing.kt`: Standardized 8dp grid spacing and corner radius hierarchy across cards, pills, sheets, and touch targets.
- **Key User Flows**:
  1. **Home / Dashboard**: Daily readiness readout, active routine summary, quick-launch workout session card, and calorie/macro telemetry.
  2. **Active Workout & Set Logger**: Clean exercise cards, plate calculations, rest timer floating controls, RPE/RIR selector bottom sheet, and rapid weight/rep adjustments.
  3. **Nutrition Hub**: Visual calorie budget ring, macro splits (protein, carbs, fats), and logged meal list.
  4. **Progress & Analytics**: Volume trends, muscle recovery heatmaps, and 1RM projections.

#### Interactive Feedback & Motion
- Tactile ripple states on all interactive elements with minimum 48dp touch targets.
- Staggered entry transitions and smooth animated visibility for collapsible exercise cards and bottom sheets.
- Non-blocking resting overlay with sound and haptic vibration queues.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Token-First Migration vs Screen-by-Screen Refactor**
  - *Chosen Approach*: Establish and stabilize core theme tokens (`Color.kt`, `Type.kt`, `Theme.kt`, `Shape.kt`) first, resolving all pending theme reference errors before redesigning individual screen composables.
  - *Why*: Eliminates compilation errors across shared UI components (`IndustrialCard`, `ReadinnessPetalDial`, `TactileButton`) and guarantees visual consistency across all screens.
  - *Alternatives Considered*: Modifying screens ad-hoc would cause cascading compile breakage and visual mismatches.

- **Decision 2: Preserving Domain Engine Integrity**
  - *Chosen Approach*: Decouple all UI visual changes from underlying engines (`ProgressionEngine`, `FatigueEngine`, `TdeeEngine`, `MacroEngine`, `WorkoutSessionManager`).
  - *Why*: Ensures the scientific calculation layer, Room DAO queries, and unit tests continue to function flawlessly while UI components are restyled.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   Theme & Design Tokens                │
│    Color.kt  │  Type.kt  │  Shape.kt  │  Spacing.kt     │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                  Reusable UI Components                │
│  TactileButton  │  IndustrialCard  │  ReadinessDial    │
│  SetLoggingRow  │  MuscleHeatmap   │  RestTimerOverlay │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                      Screen Layer                      │
│   HomeScreen    │   TrainScreen    │  NutritionScreen  │
│ ProgressScreen  │ PlanBuilderScreen│  SettingsScreen   │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│            ViewModel & Session Management              │
│       WorkoutSessionManager  │  FitnessViewModel       │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│              Data Persistence & Domain Engines         │
│   FitnessDatabase (Room)   │   DataStore Preferences   │
│   ProgressionEngine        │   FatigueEngine / TDEE    │
└────────────────────────────────────────────────────────┘
```

#### Phased Execution Pipeline
1. **Phase 1: Design System & Token Foundation**: Ingest color hexes, typography rules, and theme tokens into `ui/theme/`, fixing all unresolved references and building cleanly.
2. **Phase 2: Common Component Library**: Implement or update reusable primitives (buttons, cards, badges, dial indicators, inputs).
3. **Phase 3: Screen Overhaul**: Systematically rebuild screens according to user screenshots:
   - Screen A: Home / Dashboard & Bottom Navigation
   - Screen B: Active Workout & Live Set Tracker
   - Screen C: Readiness, Progress & Analytics
   - Screen D: Nutrition & Settings
4. **Phase 4: Polish & Verification**: Run `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` to verify complete compilation and regression test passes.
