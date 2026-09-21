# Exact output return optimization

## Changes

- Exact CPU insertions defer repeated changed-key notifications until all ledgers
  for that synchronous call settle. Each affected key is notified once. Inventory
  ownership and acceptance are unchanged; reentrant observer insertions take the
  immediate path. Ordinary CPUs and simulations retain their existing behavior.
- Optional profiling records CPU insertion, waiting accounting, inventory and
  observer time. Production samples one in 64 calls, retains the exact observed
  call count, and logs at most once per 10 seconds of activity. Stage times may
  overlap observer time in the uncoalesced baseline; they are not additive.
- A schema-checked optional Omni mixin transfers UselessMod's live, CPU-bound
  intermediate output ledger directly to that exact CPU. The original queue,
  outstanding-output total, CPU waiting credits and inventory change together.
  One key's BigInteger balance replaces many long insert calls.

## Direct-transfer boundaries

The source must have a valid normal AE return target, support native batches,
and be on the same grid as an active CPU. The receipt must still belong to the
CPU's current exact job; cancelled or rebound receipts cannot accept output.
Delivery waits until after the enqueue tick. Final outputs retain the normal
requester/storage path. Old queues restored without live receipt tokens, foreign
CPUs, disabled bridges and unsupported layouts retain ordinary network return.

The CPU accepts no more than its current exact output debt. It invokes the source
debit before updating its own ledgers, then notifies observers after all local
state is committed. The source is marked dirty even if a subsequent observer
throws. Full entries use UselessMod's original completion callback and are removed
once; partial/unclaimed output stays in the original persistent queue. Fatal
ownership inconsistencies do not silently fall through and replay a transfer.

Direct work has a shared 2 ms/tick budget, inspects at most 256 queue entries per
machine pass, and reports its real elapsed time to UselessMod's existing global
work budget. Its hook runs after the furnace updates its adaptive output budget,
so clearing a queue early does not falsely make an active machine appear idle.
UselessMod's jar itself is unchanged.

## Configuration

Active global `config/omnisequence-transfinite-server.toml`, under
`[omni_computation.dispatch]`:

- `omni_coalesce_return_notifications = true`
- `omni_direct_native_output_return = true`
- `omni_profile_exact_returns = true`
- `omni_return_profile_sample_interval = 64`

`config/useless_mod-server.toml`: `ae_output_return_budget_millis` changes from
8 to 12. The furnace consequently derives a 24 ms global work-budget reference
instead of 16 ms. It still dynamically throttles when its measured work exceeds
that reference. This is a controlled configuration increase, not proof of a
particular modpack throughput improvement.

Logs distinguish `Omni exact return profile` (sampled legacy return costs) and
`Omni native output transfer` (direct keys, completed batches, equivalent long
segments avoided, and adapter time). Disable profiling after collecting enough
modpack data if diagnostics are no longer needed.

## Evidence

`direct-return-final-runtime.log` uses the real transformed AE2 CPU and real
UselessMod queue/ledger classes with controlled grid and machine-host fixtures.
It verifies whole/partial debit, no repeat delivery, original completion callback,
rejection across grids/offline CPUs/cancelled jobs, final-output fallback, source
return availability, disabled compatibility, and unbound/restored queue fallback.

One transfer moved 37,778,931,862,957,161,705,472 intermediate items (4096 long
segments) with zero long storage insert calls. A partial case accepted half and
retained the remainder for ordinary network return.

The CPU microbenchmark alternates enabled/disabled order after warmup. Changed
key notifications fall from 8193 to 4096 for 4096 long insertions. Timing varied
across local runs; the final median was 4.9983 ms vs 4.8832 ms. This modest path
improvement is not a measured full-pack TPS gain. The direct-transfer operation
count reduction is the main optimization for enormous intermediate quantities.
Actual live-world throughput must be evaluated after restart with the saved
pre-change log and new sampled diagnostics.
