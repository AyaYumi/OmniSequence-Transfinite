# BigInteger intermediate inventory and native dispatch

This replaces the temporary intermediate-output headroom limit documented in
`exact-intermediate-chain-fix-20260919.md`.

## Ownership and persistence

`OmniExactInventory` owns physical stock beyond AE2's long inventory window.
Incoming intermediate output is split between the window and a BigInteger
overflow ledger, so KeyCounter saturation cannot discard accepted items.
The crafting inventory view exposes the complete balance to extraction,
simulation, fuzzy template lookup, and reinjection. Explicit infinite inputs
remain a separate virtual view and are never refunded as physical stock.

The overflow ledger is serialized independently of the executing job. Finishing
or cancelling clears the task/output ledger but preserves actual stock.
`storeItems` exposes bounded refund windows before and after AE2's normal
network-storage pass; storage backpressure and offline grids keep the rest in
the CPU. A replacement job is blocked while overflow refunds remain. Core
removal snapshots include overflow; retiring live CPUs clear their old copy
after ownership transfers to the retained snapshot.

## Native admission

Native providers receive one unexpanded CPU-task prototype. Finite inputs are
aggregated across all slots by AEKey; available batch count is derived from
their exact remaining balance plus the already-extracted first prototype.
Only keys explicitly marked infinite are excluded from physical reservations.

The provider's advertised capacity caps the request. Before committing, the
CPU reserves all extra finite input in BigInteger arithmetic. Rejection or an
exception rolls the reservation back; acceptance commits it once. Task progress
and output credits advance only after acceptance. Native rejection cannot fall
through to an ordinary push that bypasses that capacity decision. Another
provider can be tried when the first machine has no capacity.

The original UselessMod smart-doubling/base-operation conversion remains in
place. Machine thread limits, energy, output segmentation, admission checks,
and CPU work/time budgets remain authoritative. Folded native batches need not
appear as multiple physical AE processing tasks in the furnace GUI.

Finite reusable/damage/container recipes retain the conservative ordinary
dispatch path. The BigInteger finite path is enabled for homogeneous consumable
prototypes without container returns. Legacy non-native adapters keep their
explicit-infinite-input restriction.

## Status and verification

Applied Enhancements now transports exact stored amounts as well as pending
and active amounts. The renderer and incremental entry replacement preserve
all three. Client and server must use matching updated jars.

Runtime regression uses the real transformed AE2 CPU and installed UselessMod
classes with controlled test providers. It verifies:

- One producer push emits 2 * Long.MAX_VALUE intermediate items without loss.
- Stock and task identity survive serialization/reload between stages.
- A native downstream rejection returns all finite materials without progress.
- A subsequent single downstream native push consumes both long windows.
- A full first provider does not block a second available provider.
- Cancellation retains 3 * Long.MAX_VALUE + 7 stock, survives reload without a
  job, and drains each item once over refund windows; virtual input stays absent.
- Full exact stored amounts survive status packet encoding and client entry copy.
- Existing exact final-output/window, smart-doubling and omniversal tests pass.

Evidence: `exact-inventory-final-runtime.log`,
`src/test/java/com/atir/molecularmanipulator/crafting/OmniExactInventoryTest.java`.
The full in-game furnace/solar-sail scenario still requires modpack testing;
these tests do not assemble a physical furnace or claim a measured throughput.
