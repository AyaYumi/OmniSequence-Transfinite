# OmniSequence: Transfinite 1.3.7 Update Log

Release date: 2026-07-30

## Target Build

- `omnisequence-transfinite-1.3.7.jar`
  - Minecraft 1.21.1
  - NeoForge 21.1.230+
  - Applied Energistics 2 19.2.17+

## Multiblock Layout Update

- Both fixed multiblocks now have current layouts that leave their large
  visual-effect centers free of physical blocks.
- The Sequence Array leaves local `(0, 29, 0)` empty and moves its physical
  core to the top anchor at `(0, 36, 0)`.
- The Omni-Computation Core leaves local `(0, 17, 0)` empty and moves its data
  entangler to the front lower pylon at `(0, 9, -12)`.
- Complete legacy layouts remain valid, formed, and fully operational.
- When a legacy layout is detected, the controller displays an optional update
  prompt. The player decides whether to migrate it; rebuilding is not required.
- Projection, automatic construction, dismantling, JEI previews, material
  lists, and GuideME pages all use the correct current or legacy layout.

## Construction and Compatibility Fixes

- Sequence Array automatic construction now correctly uses connected ME
  Network materials in Survival mode.
- Creative-mode construction no longer waits for materials or creates refunded
  blocks after a failed placement.
- Existing supported construction methods and complete legacy structures
  remain compatible.

## Autocrafting, Reusable Inputs, and Cancellation

- Item-substitution patterns still fall back from MAX_FAST planning, but are
  permanently eligible for compatible runtime batch dispatch. AE2 selects the
  actual input, and the retired `omni_batch_allow_substitution_patterns` key is
  removed from existing TOML files without resetting other custom values.
- The Assembler Matrix Sequence Rewrite Core and Sequence Array persist every
  accepted reusable-input batch across saves, chunk unloads, and restarts.
- Canceling the AE2 job persistently stops every remaining provider-side
  execution and refunds the exact unused materials plus the reusable item's
  current state. Completed outputs remain valid and canceled work cannot resume
  after reload.
- Same-key and unbreakable remainders can run as one reusable batch.
  Finite-durability tools batch only when each craft deterministically adds
  exactly one damage. Unbreaking-enchanted, random, contextual, and key-changing
  transitions retain AE2's original one-craft path.
- Batch expansion uses AE2's native pattern-power calculation over the actual
  combined inputs, preserving the original crafting-energy semantics.

## Interface and Documentation

- All GuideME pages now appear under a dedicated top-level
  `OmniSequence: Transfinite` category while retaining item-page links.
- Controller screens retain the vanilla translucent world backdrop while
  responsive scaling covers the complete viewport.
- Controller screens scale down when necessary, with slots, buttons, tooltips,
  and JEI ghost ingredients following the same coordinates.
- Matter Sequence details use a compact Shift-expand prompt by default. The
  client setting supports `DISABLED`, `HOLD_SHIFT`, and `ALWAYS_VISIBLE`.
- Pattern inventories support debounced cross-page input/output search while
  retaining AE2's complete encoded-pattern hover details. Just Enough
  Characters remains optional and enables Pinyin matching when installed.
- English label/value rows are width-aware, preventing titles, page numbers,
  sequence amounts, quantum status text, and other values from overlapping.
- The two matter-job states and counters fit independently inside their
  96-pixel columns.
- The Omni-Computation fixed-capability notice now uses normal-size wrapped
  text.
- AE2's narrow crafting CPU list displays compact names such as
  `Omni Core · L1`; hovering still shows the complete lane name.
- English and Chinese translation keys were checked as complete pairs.

## Projection Performance

- Ghost-block projections now reuse 16x16x16 section vertex buffers instead of
  tessellating every projected block every frame.
- Changed sections rebuild incrementally, at most two per frame, while
  unchanged geometry is reused.

## Installation and Existing Worlds

1. Stop the client and server, then back up the world.
2. Remove or disable older active `omnisequence-transfinite-*.jar` files.
3. Install only the JAR matching the Minecraft version and loader.
4. Keep exactly one active OmniSequence JAR in each `mods` directory.
5. Existing complete multiblocks may continue running unchanged. Use the
   controller prompt only if you want to migrate to the effect-cleared layout.

## SHA-256

- `omnisequence-transfinite-1.3.7.jar`
  `22ECA3C8A925DC6D43E4BC2D18F5A4A1F2027B1DC12974D2E505F1899E987304`
