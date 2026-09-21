# Cumulative completed output display

UI behavior superseded by [last accepted batch display](last-batch-status-20260919.md)
after the user clarified they want to inspect the size of an individual push.

`正在合成` is the outstanding output waiting to return; instant batches can
legitimately show zero between submissions. `可用数量` is present inventory and
falls when downstream recipes consume it. Neither is a cumulative completion
counter.

The exact job now records positive MODULATE waiting-inventory settlements by
AEKey in a separate BigInteger map. Simulation, input reinjection, provider
admission callbacks and downstream consumption do not increment this map. It
is persisted with the job and copied on task rebinding. `已完成` displays this
cumulative value, compact in cells and fully expanded in tooltips.

The status protocol carries completed counts with each entry's stable serial.
Rows containing only completed history are retained until the job ends, even
when stored/active/pending have all reached zero. Full discovery and incremental
replacement both retain these rows.

Also fixed the inventory label: AE2 uses `GuiText.FromStorage` for the numeric
available-amount row. `GuiText.Stored` is a standalone heading, which caused the
spurious `现存` line and left the original long projection unchanged. The shared
label formatter now updates FromStorage and is exercised by runtime tests.

Verification in `exact-completed-runtime.log` covers:

- 18,446,744,073,709,551,614 completed outputs before and after all stock is used.
- Save/reload preservation and simulation leaving the counter unchanged.
- Full and incremental packets, stable serials, client entry copying, and
  retaining completed-only rows.
- Compact `18.4E`, full tooltip numbers, and actual FromStorage label replacement.
- Existing crafting, cancellation/refund, smart-doubling and status regressions.

Old saved jobs do not contain historical completion counters. Their cumulative
display starts when this version begins observing deliveries; a newly submitted
job records the entire history. No completed amount is inferred from current
stock or fabricated from prior logs. Both updated jars must be installed.
