# APEX FIT Night Ledger — Remaining Codex Work Ledger

This document is the version-controlled delta for the approved `APEX FIT — FINAL FUNCTIONAL INTEGRATION PASS`.

Baseline branch: `chatgpt/functional-integration-safe-20261115` (PR #33).

Status legend:
- **DONE** — implemented on PR #33; preserve and verify.
- **PARTIAL** — safe/domain work is done; Codex still owns the listed render/UI work.
- **CODEX** — requires Stitch fidelity, Compose UI work, emulator/device validation, or visual judgment.
- **VERIFY** — existing behavior should remain; do not rewrite without a demonstrated defect.

The original 0–50 acceptance criteria remain authoritative. This file prevents duplicated work.

All new screens/features are governed by `DESIGN.md`, including features added after this ledger was written. The Night Ledger design, truthfulness, accessibility, performance, navigation, motion, and validation rules are not optional per-feature styling suggestions.

## Pointer ledger

| # | Status | What is done / what remains |
|---|---|---|
| 0 | VERIFY | Keep exactly 4 bottom destinations: Today, Train, Nutrition, Progress. Train remains Program, Workout, Plans. No new top-level tabs. |
| 1 | PARTIAL | `DESIGN.md` now makes Night Ledger authoritative. **Codex:** migrate the actual Compose screens/tokens/fonts/icons to the approved Stitch references. |
| 2 | CODEX | Preserve approved screen priority/hierarchy during visual migration. Do not let new features overpower the primary task. |
| 3 | DONE | Free Exercise DB photos are disabled in production; false public-domain claims removed; instructions/videos preserved; regression coverage added. |
| 4 | PARTIAL | Written Form Guide fallback already works without image media. **Codex:** migrate/build the approved Exercise Detail presentation and reuse existing form content/media. No AI biomechanics. |
| 5 | VERIFY/PARTIAL | DEMIC mapping + nocookie lazy playback already exist in `ExerciseMediaPanel` / `exercise_videos.json`. **Codex:** ensure approved Exercise Detail and active-workout Form Guide expose it; no standalone Videos area. |
| 6 | PARTIAL | Deterministic `SessionHeroResolver` + tests implemented; `HomeViewModel.todayHeroCategory` exposes real-session focus. **Codex:** bind categories to verified local hero assets and render-test all split types. |
| 7 | CODEX | Today hero presentation: verified local assets only, contextual, full bleed, no card/radius, honest fallback/no image. |
| 8 | CODEX | Bring the final approved photography assets/manifest into the shipping repo and preserve source URL, ID, photographer, license, use category, download date, brand notes. |
| 9 | PARTIAL | Active Today now opens real readiness factors and includes the non-medical estimate disclaimer. **Codex:** restyle to approved Night Ledger sheet and prioritize today's relevant factors visually. |
| 10 | VERIFY/PARTIAL | Existing readiness engine uses cautious recommendations. **Codex:** surface actionable calm wording only; no motivational filler. |
| 11 | DONE/VERIFY | Existing deterministic workout-duration estimator preserved; active Today now labels duration approximately. Do not rewrite engine without evidence. |
| 12 | DONE/PARTIAL | Active Today now explains what duration uses and what it does not model. **Codex:** restyle interaction to approved UI. |
| 13 | DONE | Plans exposes Import from text and reuses existing importer. |
| 14 | DONE | Plan Builder/direct Plans routes reuse one importer/review/save pipeline; direct cancel returns to Plans. |
| 15 | DONE | Imported plans inherit user's current goal, allow review/edit, and no longer silently hardcode General Fitness. |
| 16 | PARTIAL | Shared exercise filtering domain logic exists. **Codex:** add Exercise Library as a secondary Train route; do not add a Train/bottom tab. |
| 17 | PARTIAL | `ExerciseCatalogFilter` supports shared search/equipment/muscle behavior. **Codex:** build ONE reusable Exercise Picker used by Library, Plan Builder Add, Swap/Substitution. |
| 18 | PARTIAL | Primary/secondary muscle filtering + ranking is implemented and tested; secondary matching is opt-in. **Codex:** expose this honestly in the picker UI. |
| 19 | CODEX | Create original APEX front/back anatomy with accessible hit regions. Do not copy Lyfta or use Free Exercise DB imagery. |
| 20 | CODEX | Keep Muscle Explorer functional/minimal: tap muscle → exercise list. No glow, cards, heatmap gradients, floating labels, radar. This rule applies to discovery; the separate Progress Muscle Volume view may use restrained discrete set-count intensity on the shared anatomy per `DESIGN.md`. |
| 21 | PARTIAL | Repo already has five onboarding stages; preference storage remains because other app code uses it. **Codex:** migrate visible onboarding order/design to Goal → Personal Details → Experience → Frequency → Ready. No mandatory Nutrition Preferences stage. |
| 22 | CODEX | Ready to Start must show real generated first week + starting calories/protein + CTA; no photo/confetti/marketing graphic. |
| 23 | CODEX | Replace current trophy/card/stat-grid Session Complete UI with approved flat training-receipt hierarchy using real values only. |
| 24 | CODEX | Add restrained earned PR feedback only: one brief haptic + ~150–220ms reveal. No confetti/trophy/fake celebration; avoid duplicate session-feel input. |
| 25 | CODEX | Progress Overview: Weight trend, Strength, Consistency, Recovery, Explore. No bento/stat wall/dashboard. |
| 26 | CODEX | Progress Explore must preserve real destinations: PRs, History, Measurements, Volume, Muscle Recovery, Fatigue & Deload. No dead rows. |
| 27 | CODEX/VERIFY | Preserve real PR data/history; redesign presentation without badge wall or fake praise. |
| 28 | CODEX/VERIFY | Preserve real workout history/detail including duration, state, sets, weight/reps/RPE. Use drill-down. |
| 29 | CODEX/VERIFY | Preserve measurements/history; only show trends with enough real data. |
| 30 | CODEX/VERIFY | Replace the radar/spider volume UI with **Progress → Explore → Volume** using the `DESIGN.md` Muscle Volume contract: Week/Month, original APEX front/back anatomy with restrained discrete intensity based on completed direct working-set counts, accessible ranked list, muscle drill-down with contributing exercises/set counts, and secondary exposure shown separately rather than invented fractional-set math. Keep recovery/growth semantics separate. Preserve existing trustworthy volume data and add/adjust aggregation tests as needed. |
| 31 | CODEX/VERIFY | Preserve recovery calculations; use readable rows, optionally reuse the same approved anatomy component. |
| 32 | CODEX/VERIFY | Preserve fatigue/deload engines/protocol; remove cockpit/gauge UI and present clear status + action. |
| 33 | PARTIAL | Active Today now withholds weight chart until 3 distinct dated weigh-ins. **Codex:** implement the same truthful state and real longitudinal chart on Progress with goal-aware interpretation. |
| 34 | PARTIAL | Active Today now says **Log weight**. **Codex:** enforce the same term everywhere including Progress. |
| 35 | CODEX | Preserve progressive disclosure: primary value on current screen, deep history under Explore; do not show everything at once. |
| 36 | VERIFY/CODEX | Existing set logging, RIR, importer, and meal-delete flows already provide core feedback/undo. **Codex:** audit all migrated high-value actions for immediate visible confirmation. |
| 37 | CODEX | One restrained motion language: ~120–180ms routine, ~180–220ms earned confirmations. No random bounce/pulse/infinite motion. |
| 38 | CODEX | One approved icon family; inactive outline / active stronger-filled where available; labels remain visible; no icon pods/mixed sets. |
| 39 | CODEX | Implement/verify honest empty/loading/error states across plans, workouts, weights, PRs, history, measurements, recovery, media, search, importer, offline video. Never fill with sample data. |
| 40 | PARTIAL | Removed fabricated Today recent-session metrics; readiness disclaimer added; notification copy made neutral. **Codex:** final user-facing copy audit across migrated screens. |
| 41 | PARTIAL | Removed streak-at-midnight pressure, fake-coach language, urgent plateau framing; Today uses rest-day-safe weekly session count. **Codex:** final whole-app dark-pattern audit. |
| 42 | PARTIAL/CODEX | Domain derivations for hero/filtering are hoisted out of UI. **Codex:** profile real migrated screens; avoid network hero images, eager WebViews, N+ queries, heavy recomposition/God composables; verify fast tab switching. |
| 43 | CODEX | Device/emulator accessibility pass: 48dp targets, TalkBack, large fonts, keyboard, contrast, system bars, phone layouts; map needs non-visual search/filter path. |
| 44 | DONE | `DESIGN.md` is the single Night Ledger source of truth. |
| 45 | VERIFY | Continue implementation in small screen/feature slices; build/test/render after each major surface rather than one giant migration. |
| 46 | VERIFY | Preserve Room, DataStore, Hilt, repositories, importer, matcher, progression/readiness/fatigue/recovery/duration/nutrition/video/workout persistence unless a concrete bug is proven. |
| 47 | CODEX | Run all eight end-to-end journeys on an emulator/device: normal workout, manual plan, import plan, custom/bro split hero, exercise discovery, Plan Builder picker, Progress destinations, insufficient-data states. |
| 48 | CODEX | Run final anti-AI visual audit against each changed screen and approved Stitch references. |
| 49 | SHARED | Final correctness gate: PR #33 covers safe/domain items; Codex must verify the full UI/device checklist and report unknown items as unresolved. |
| 50 | CODEX | Final report must include branch/PR, screens/flows/components, media, hero resolver, import/picker/videos/readiness/duration/Progress/onboarding states, performance, accessibility, tests, render comparisons, build, limitations. |

## Important existing code Codex must reuse

- `SessionHeroResolver` / `SessionHeroCategory`
- `HomeViewModel.todayHeroCategory`
- `ExerciseCatalogFilter`
- `ExerciseMediaCatalog`
- `ExerciseMediaPanel`
- `FormGuideSheet`
- `WorkoutImportFlow`
- `WorkoutImportViewModel`
- existing readiness, duration, fatigue, recovery, progression, PR, nutrition, Room/DataStore/Hilt architecture

Do not reimplement these simply to fit a screenshot.

## Required Codex sequence

1. Start from PR #33 HEAD, not stale `main`.
2. Run baseline tests/build.
3. Set up a usable emulator/AVD if possible.
4. Migrate shared Night Ledger theme/tokens/fonts/icons.
5. Migrate Today and bind `todayHeroCategory` to verified local hero assets.
6. Build unified Exercise Picker + original Muscle Explorer UI on top of `ExerciseCatalogFilter`.
7. Migrate Exercise Detail/Form Guide/video surface.
8. Redesign Workout Complete.
9. Migrate five-stage onboarding + Ready payoff.
10. Migrate Progress overview and all six detail destinations.
11. Global empty/error/loading, motion, icon, performance and accessibility pass.
12. Run the eight end-to-end flows.
13. Compare every migrated Android render to its approved Stitch reference.
14. Run full CI and report unresolved items rather than calling them done.

## Definition of done

Compiling is necessary but insufficient. Completion requires:
- real Android flows,
- passing automated tests/build,
- truthful states,
- preserved business functionality,
- approved Night Ledger fidelity,
- emulator/device validation wherever the environment permits.
