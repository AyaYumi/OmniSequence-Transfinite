# Same-tick direct return and verified Y-sized admission

The fixed Y ceiling below is superseded by [adaptive direct limits](adaptive-direct-limit-20260919.md).
The same-tick routing and verified-route safeguards remain in effect.

## Actual modpack conflict

Applied Enhancements' `AelisSmartCycleBatchProvider` registers an Omni
post-accounting adapter at priority 100. Its ImmediateOutputFlush calls
`flushQueuedCraftingOutputs(true)` immediately after CPU accounting. That path
bypasses the normal block-tick hook, drains same-tick outputs as long chunks,
and leaves the native direct-return adapter nothing to transfer. This explains
the live log's roughly two million segmented CPU inserts per ten seconds despite
the previous direct-return fixture passing.

Omni now registers a native-only post-accounting adapter at priority 300. It
unwraps provider proxies and attempts direct transfer with same-tick eligibility
only at this known post-accounting point. Ordinary, unbound and final-output
remainders wait for the furnace's normal bounded block-tick return. Cancellation
force flushes are not reinterpreted as safe direct-delivery points.

## Conditional capacity

A source manager/grid pair becomes verified only after an actual direct transfer
debits its source queue and credits the CPU. Verification uses weak references
and is checked against the current source grid and available output route.

For a live, active exact CPU submitting intermediate-only batches without
container returns, a thread-local admission scope allows that verified manager
to expose a separate output-segment budget during capacity/admit/commit. Native
input windows, thread counts, recipe/energy availability and dynamic throttling
still apply. The ordinary manager budget is never overwritten.

The requested count is also capped using exact per-key output totals. Default
`omni_direct_native_batch_y = 1` targets at most 10^24 output units per key per
batch, rounded down to whole scaled tasks. Config currently permits 1–2 Y.
Unverified managers, foreign grid scopes, final outputs, disabled direct return
and ordinary callers retain the original native capacity path. The first small
successful batch establishes the direct route; later batches can use Y capacity.

Diagnostics distinguish same-tick/block-tick success, no live binding, source
offline, budget exhaustion, grid mismatch, inactive CPU, stale binding, final
output and missing waiting debt. Logs remain rate-limited.

## Verification

`y-admission-final-runtime.log` runs actual transformed CPU and Useless queue
classes with controlled host/grid/provider fixtures. It reproduces same-tick
queue age, verifies priority 300 prevents the old forced-flush adapter, admits
10^24 units through the native adapter, and verifies ordinary budget restoration
and foreign-grid refusal. Direct transfer tests now move 1Y and partially accept
0.5Y while preserving source/destination ownership and original fallback.

The live solar-sail world still needs a restart/retest. The configured 1Y is a
batch ceiling, not a promise that every push reaches that size or a measurement
of final-product throughput. UselessMod and Applied Enhancements jars are not
modified by this fix.
