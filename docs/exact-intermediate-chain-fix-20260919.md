# Intermediate output capacity and downstream scheduling

Superseded by [BigInteger intermediate inventory](exact-inventory-native-dispatch-20260919.md).
The temporary intermediate headroom cap below has now been removed for native batches.

The 00:45 modpack log confirms native batches now execute the scaled number of
base operations, including batches producing about 1.2082e23 items. Dispatch
then stalls in a multi-stage compressed-sail order.

Two CPU defects remained:

1. Native BigInteger admission reserved an unlimited output ledger but did not
   reserve the physical CPU inventory used for intermediate products. Repeated
   long-sized returns saturated the inventory counter, silently losing stock
   needed by downstream recipes.
2. An upstream recipe with no output room set the global dispatch-stopped flag.
   Tasks later in the same pass could not consume that full buffer.

Native intermediate admissions now reserve headroom for stored, waiting, and
unwindowed outputs together. The limit accounts for the extracted recipe's
declared outputs and containers. It is expressed in the original CPU task units
before the Useless bridge converts those units. The compatibility admission
loop also respects this aggregate reservation instead of resetting its limit
after each accepted sub-batch. Final products delivered through the crafting
link retain BigInteger rolling windows.

Output backpressure now skips that producer, preserving the rest of the CPU
dispatch pass. Existing CPU-wide time and work budgets still bound the pass.

The transformed-runtime regression runs actual `executeCrafting` and `insert`
methods with a controlled provider. A two-stage order produces and consumes
2 * Long.MAX_VALUE intermediate items and yields two final outputs. Producer
tasks are deliberately ordered first on every tick. The old code fails the
capacity assertion; the fixed version completes both cycles, accepts all
intermediate returns, and leaves zero intermediate stock. AE ticks advance in
the test so the normal adaptive provider cooldown remains active.

Evidence: `exact-chain-before.log`, `exact-chain-after.log`
(`INTERMEDIATE_CHAIN_SMOKE_PASSED`), `exact-chain-release.log`.
The actual UselessMod jar is loaded in this runtime; the test provider is
synthetic, so complete modpack furnace behavior still needs in-game validation.

Intermediate throughput is now bounded by the real CPU buffer and downstream
consumption. This prevents loss; it does not turn the physical CPU inventory
into a BigInteger store. Old jobs whose inventory already saturated cannot
recover the discarded quantities and should be cancelled and resubmitted.
