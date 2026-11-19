# Third-party assets

## MuscleMap flat-vector anatomy

- Repository: [Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap)
- Pinned source commit: [170cfacda79376dd59ac0cf2f7dfab8ff9f4443a](https://github.com/Jsplice/MuscleMap/commit/170cfacda79376dd59ac0cf2f7dfab8ff9f4443a)
- Upstream files: `packages/assets/src/male-front.ts` and `packages/assets/src/male-back.ts`
- Bundled derivatives: `app/src/main/assets/anatomy/male_front.json` and `male_back.json`
- Provenance: the upstream project says these muscle paths are original vector work hand-traced in Inkscape.
- License: MIT. The upstream copyright and complete license text are included at `app/src/main/assets/licenses/MuscleMap-MIT.txt`.
- App use: native Compose Canvas rendering, front/back selection, tappable muscle regions, direct-set intensity, and APEX mineral-blue selection.
- Excluded: the upstream reference-body photographs. No raster anatomy or AI-generated body image is bundled.

The MIT license allows commercial use, modification, and redistribution when its copyright and permission notice remain with substantial copies. This entry records source provenance; it is not a legal opinion.

## Exercise form reference imagery audit

No third-party start/end exercise images are bundled in production. Written instructions remain the fallback, and the existing technique-video embed is streamed on demand.

Candidates reviewed on 2026-11-20:

- [Free Exercise DB](https://github.com/yuhonas/free-exercise-db) — excluded. Its maintainer says the repository's data is Unlicense but the image origins and royalty status are unknown; image files are not used.
- [Open Exercise Illustrations](https://github.com/hitmanrepo/open-exercise-illustrations) — excluded. The project claims CC0, but its README says the illustrations are AI-generated and not reviewed by a qualified coach or medical professional. That conflicts with the APEX form-reference requirement to avoid AI exercise-form imagery and prioritize correct biomechanics.
- [Flow Exercise Dataset](https://github.com/ozansozuozgit/flow-exercise-dataset) — excluded for now. It claims CC0 for custom SVG artwork, but its README says only 13 of 1,257 animation prompts survive and that it cannot claim independently proven clean-room origin or professional form review.

No images from these repositories were copied into the app. Reconsider only a source with per-asset rights provenance and exercise-form review, or original non-AI artwork with documented permission and review. This records an engineering source audit, not a legal opinion or blanket rights clearance.

