# Apex Fit — integrated release acceptance register
Created 2026-11-28. Baseline draft #63, 2638590f3b7401da29de304a26637d07f426aeb1.
This is a release gate, not proof of feature completion. All 14 have initial code. Do not merge overlapping stacked PRs independently. No production merge until gates are met and owner approves.

## Evidence rules
- A green predecessor is not evidence that the combined head passes. Record exact SHA, workflow IDs, test results, lint, APK, generated Room schema, and screenshot artifact.
- Room-generated schema v30 must be committed from the actual CI artifact, not reconstructed or given an invented identity hash. Test 29→30 and all supported migration paths, preserving old rows and foreign keys. See [Android migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions).
- Physical device and screenshot review are separate gates; a render job is not visual acceptance.
- Unknown historical evidence is not zero, a missed session, measured effort, or comparable resistance.

## Acceptance gaps against original technical plan
| Feature | Current implementation | Required closure |
|---|---|---|
| A01/F01 volume | bounded week/month/year | attribution snapshots, unknown coverage, chart/detail parity and 100k-set benchmark |
| A02/F02 heatmap | shared fixed direct count bands | source drill-through, attribution and loading/unknown parity |
| A03/F05 frequency | unique dates and adjacent gaps | cross-screen verification with corrected/partial history |
| A04/F04 comparisons | matched finished days, full past periods | regression of shorter months, coverage and correction UI |
| A05/F03 targets | local effective-week history | verify editing, reset/export, plan unchanged |
| A06/F08 strength | observed load/reps | explicit active-load provenance, comparable volume graph |
| A07/F09 PR | exact rep, manual setup confirmations | active capture, stale cleanup, full source-workout navigation, legacy distinction |
| A08/F10 e1RM and relative | conservative 2–5 rep-limit confirmed set, prior 1–7-day daily mean | verified effort/weight timing, relative trend and source audit; reconcile deliberate stricter product policy with original spec |
| A09/F07 calendar | actual records, current plan projection | immutable effective-dated revisions and occurrence IDs, explicit missed/excused, adherence; no retroactive reconstruction |
| A10/F06 reports | individual workout report | weekly/monthly reports, shared aggregate evidence, known-schedule adherence, source records |
| A11/F11 goals | local goal definitions, baseline and recalculation | preferred units, exercise-detail entry, replacement UX, source and export tests |
| A12/F12 photos | private no-backup JPEG copies and deletion | actual offline picker, format/orientation/GPS metadata, process death, OEM transfer, memory and reset tests |
| A13/F13 groups | first consecutive session-local group | arbitrary/multiple permanent groups, importer confirmation, explicit zero rest, structured history, correction and resume tests |
| A14/F14 advanced bouts | parent + validated payload, atomic completed-bout editor | live continuation logging, correction, warm-up interface, grouped compatibility, full metric parity; no automatic progression until approved |

## Integration order
1. P0 exact-head CI and schema artifact, resolve failures without weakening invariants.
2. P0 shared receipt/history facts: valid working bouts, actual reps and logged rest include continuations once; raw load compatibility field is not a comparable metric. Verify no false PR/progression or duplicate commits; legacy data unchanged.
3. P0 transactional capture of load/setup/effort and muscle attribution, explicit unknown migration.
4. P1 durable schedule revision/occurrence ledger and period reports.
5. P1 plan/import circuit grouping, then advanced live logging, correction and integration.
6. P1 secure export, privacy/reset, 100k-set performance and actual device journeys. Final visual review and user approval.

## Explicit stop conditions
Any failed unit test/lint/compile, destructive migration, duplicate workout write, missing generated schema, rest checkpoint mismatch, unconfirmed comparable load, unsupported legacy source classification, or photo leakage blocks release. Do not claim acceptance from source inspection alone.

## Integration evidence, 2026-11-28
- A13 head 77ec040bdac336a247d6c40e9dde70ce54cab3d0 passed Android 46100043285 and palette 46100043340.
- A14 integration 0179dc54cc6e48c17773d89b0cda0fc20faced50: Android 46100433151 had 511/513 tests pass; two migration FileNotFoundException failures because generated v30 schema was not checked into source. Palette and render step passed. Generated artifact 15244968943 verified against SHA256 bb0795144a998c2f21f750df0b7ba826100f846a9e6fbbdd0d05616b1f8beaed; exact v30 file Git blob 0cc12e4b5e32084b7001079b346935f6c1d4ada8 added in 2c88de525349dc8b1f5ba29816fdc4c189b73eb2. Migration rerun required.
- Removed inherited RPE from extra-set creation and corrected RPE 0 label to Unrated; clarified legacy indirect attribution. Added two regression tests. This does not supply historical effort or load provenance. Full v31 work gated on v30 migration result.

### Next schema contract (proposed, not implemented)
Use additive nullable provenance fields or a one-to-one source-set table in a single transactional migration after v30 is verified. A historical null stays null. Snapshot muscle attribution at exercise occurrence creation (or explicitly labelled commit-time snapshot if only available then); preserve primary recorded label and canonical secondary list; an empty explicitly captured list differs from missing metadata. Group by per-set snapshot when aggregating, not by latest library entry. Library edits cannot mutate a snapshot. Legacy fallback only labelled inferred; absent catalog information explicitly unknown. New effort source enum USER_RPE / USER_RIR / EXPLICIT_OUTCOME / UNKNOWN; persist nullable actual RIR and RPE independently from display/prescription defaults; edits invalidate matching load confirmations, and rest-only edits must not change provenance. User-selected load basis and setup identifier carried from active occurrence, frozen on completed set; no category-derived per-hand/total/assistance claims; same setup can be reused only by explicit selection. Map the existing A07 manual attestations only after exact source fingerprint verification, never auto-promote legacy data. New writes and final receipt are one atomic DB transaction. CSV exports source columns and distinguishes null from explicit zero; reset removes metadata. Tests: two sets in one session with different snapshot or basis, library edit after save, old record untouched, unknown effort blocks progression and e1RM, rest-only edit preserves provenance, delete and correction invalidate/replay, migration 30→31 + full chain and generated schema committed. Do not switch the analytics consumers until provenance capture and replay agree.
