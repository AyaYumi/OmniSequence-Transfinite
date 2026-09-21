# Last accepted push displayed as Crafting

User clarified that the desired quantity is the size of one push, not cumulative
completed output. The `已完成` row is removed. `正在合成` displays the most recent
accepted batch output quantity for each output key and retains it after delivery
until the next accepted push or job change. Hover text explicitly identifies
this as the last accepted batch and lists each actual scaled input key/amount.
Unlike a sum of materials, per-key amounts do not mix items and fluid units.

Snapshots are recorded only after a successful provider decision. Native batches
scale the untouched one-task prototype by the admitted BigInteger task count;
ordinary/expanded pushes snapshot the actual extracted counters before the
provider may clear them. Legacy repeated admissions record the last individual
push, not the sum of the compatibility loop. Rejections never overwrite a
previous snapshot. Each snapshot replaces, rather than increments, its output row.

Snapshots persist with the job and travel in the same full/incremental status
packet as their entry serial. Client entry copying preserves the inputs and
output amount. Actual waiting/stock ledgers remain unchanged by this display.
Completed-only row retention is now keyed by the last-batch snapshot instead.

Validation: `last-batch-runtime.log` confirms full/incremental transfer,
serialization, input totals, retention after return/consumption, removal of the
completed label, and compact/full Crafting formatting beyond Long.MAX_VALUE.
The state unit test verifies replacement rather than accumulation; downstream
native tests verify two output units paired with 2 * Long.MAX_VALUE finite input
units and no snapshot on rejection. Existing execution/refund tests also pass.

Old saved jobs have no last-batch snapshot until their next accepted push.
Both deployed jars need to be updated together. UselessMod is unchanged.
