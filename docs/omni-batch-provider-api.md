# Omni Batch Provider API v1

For mod developers: use this interface to let Omni allocate materials and deliver
several complete crafts to your AE2 pattern provider as one atomic batch.
Ordinary AE2 providers need no new interface to keep working. For machine setup,
see the [player guide](../README.md) or the in-game G guide.

Available since OmniSequence: Transfinite 1.3.9.

Current for OmniSequence 21.3.0.5 on Minecraft 1.21.1 / Java 21, with AE2 19.2.18
and AppliedEnhancements 1.1.2+ (compile API 1.1.4). The runtime ABI remains 1.
Other languages: [中文版](omni-batch-provider-api.zh-CN.md).
See the [API index](README.md) for the separate research and planner contracts.

Use the public APIs in AppliedEnhancements for AELIS planning and cycle execution.

Implement this SPI on an AE2 machine that stores encoded patterns and acts as an
`ICraftingProvider` when it can accept several complete crafts as one atomic
transaction from an Omni-Computation Core or Transfinite Compute Nexus.

From L7, a standard AE2 Pattern Provider can batch processing patterns with no
returned inputs or output keys that also serve as inputs. The provider must be
online with an empty send queue, with blocking and crafting locks disabled.
Omni checks one input face at a time. A single input uses the target's insertion
simulation. Multiple inputs need a verified storage layout. They compete for the
same slots and can use separate storage channels when those channels are independent.

Omni rechecks the selected face at submission. Pending sends from loaded providers
toward the same inventory hold off new batches, including across both halves of a
double chest. Any accepted materials the machine takes only in part remain in the
provider's send queue on the selected side.

L0–L6 use AE2's native single-craft loop and its rolling operation window.
At L7–L9 the CPU requests as many complete crafts as the order, ingredients,
power, current recipe phase and quantity types allow. A standard provider probes
the selected face for that quantity and accepts the number that fits in one
submission. Orders share the controller's dispatch work and the server time window.
One submission can contain the entire order at any of these tiers.

Providers need a batch adapter when Omni cannot verify joint capacity. Without
one, AE2 can send individual crafts within the server-wide fallback allowance.
The server log reports multi-input orders using this fallback. Crafts with
returned items can also use native pushes. AELIS cycles share their seed stock.

The effective tier is sampled once per controller tick. A tier change switches
the route for the remaining copies while providers continue delivering stock
from batches they already accepted.

Omni-Computation Cores and Transfinite Compute Nexuses use the same provider
ABI. The standalone Advanced AE Quantum CPU executes through Advanced AE and its
own integrations.

The API contains no classes, Mod IDs, or reflection paths for a specific
integration. Any pattern-provider assembly can opt in.

## Public types

- `com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchProviderAdapterRegistry`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchProbe`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchRequest`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi`
- `com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu`
- `com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputProvider`
- `com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry`

Use `OmniBatchCraftingApi.apiVersion()` for a runtime ABI check. The matching
source constant is `API_VERSION`; do not rely on that compile-time constant for
runtime negotiation because Java may inline it.

## Provider integration

The provider continues to advertise patterns through AE2 and implements one
additional interface. The Java excerpt below uses your machine's own validation,
capacity and persistent-queue methods; implement those helpers alongside its
normal `ICraftingProvider` methods before compiling an integration:

```java
public final class ExamplePatternMachine
        implements OmniBatchCraftingProvider {
    @Override
    public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
        if (!ownsPattern(probe.pattern())) {
            return null;
        }

        long capacity = Math.min(
                probe.requestedMaxCrafts(),
                completeCraftCapacity(probe.oneCraftInputs()));
        if (capacity < 2) {
            return null;
        }

        return new OmniBatchAdmission() {
            @Override
            public long maxCrafts() {
                return capacity;
            }

            @Override
            public void commit(OmniBatchDelivery delivery) {
                var request = delivery.request();
                if (!queueCanAcceptEveryInput(request)) {
                    delivery.reject(new OmniBatchDelivery.Rejection(
                            OmniBatchDelivery.RejectReason.CAPACITY_CHANGED));
                    return;
                }

                enqueueEveryInputAndPersist(request);
                delivery.accept(new OmniBatchDelivery.Receipt(
                        OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE,
                        OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
            }
        };
    }
}
```

Both input lists preserve the original input slot, with separate entries for
distinct substituted keys. `OmniBatchProbe.Input` describes the allocation for
one craft and is used only to quote capacity. `OmniBatchRequest.Input` is the
authoritative key and total amount at commit. The CPU reserves multiples of the
selected input from its available stock.

The request also carries a unique dispatch ID, the current AE2 crafting-job ID
when available, the number of crafts to accept, and the total expected outputs.
The final request can be anywhere from two through `admission.maxCrafts()`.
An admission must accept that whole range or reject atomically at commit time.

## Ownership contract

- `prepareOmniBatch` may only inspect state or create a reversible reservation.
- `commit` runs synchronously on the server thread and must call exactly one of
  `accept` or `reject` before returning.
- `accept` is the ownership commit point. Before calling it, every input must
  already be in a durable target or in the provider's persisted queue.
- `reject` means the provider retained no material and made no irreversible
  change. Partial acceptance is forbidden.
- `CAPACITY_CHANGED` yields this provider and pattern for the current tick.
  An explicit rejection for another reason can be followed by one bounded
  single-craft attempt after the reservation is returned.
- If provider code raises a runtime exception, linkage failure, or assertion
  failure after calling `accept`, Omni still treats the delivery as accepted so
  the CPU cannot duplicate the materials by reinjecting them. Other `Error`
  subclasses, including JVM-fatal failures, are never swallowed.
- Returning without a terminal decision suspends dispatch while the CPU keeps the
  reservation for reconciliation. A delivery can be completed once on the
  server thread.
- Omni always closes the admission. `close` may release a reservation, but must
  never discard an accepted batch.
- A queued or saturated provider must keep `isBusy()` truthful until it can
  accept another AE2 dispatch. Persist queue changes before returning.
- `RECHECK_NEXT_TICK` and `SATURATED` backpressure only suppress another batch
  probe for the same provider/pattern during the current tick. They do not
  pause unrelated patterns or providers.
- Use `dispatchId` for idempotency if the provider has a durable work queue.

API v1 batches only purely consumable inputs. Recipes with
returned containers, reusable tools, or durability transitions keep the safe
one-craft path unless an existing internal Omni machine handles them. Such internal
handling does not extend the public v1 ownership contract.

The public input records carry `AEKey`, including registered addon types; each
provider decides which types it can actually process. `amount` uses that key's
native AE unit (items for item keys and mB for fluid keys). Amounts are `long`, but the provider
must check per-key addition and multiplication before accepting. An advertised
`Long.MAX_VALUE` limit does not bypass real materials, power, queue capacity, or
expected-output overflow checks.

The built-in Matter Fabrication Pattern Assembly implements this contract using
its own persistent input/output buffers and supports all registered AEKey inputs
declared by a well recipe. Research permission is checked before admission and
before queued work starts. Production limits and bonuses are evaluated when work
starts. Started work retains material, output and power snapshots across reloads;
duration follows current engine benefits with proportional progress rescaling.
Queued work retains material ownership and recipe IDs while using current definitions. Its
UI buffer size is not an unconditional batch limit. See the [well API](matter-research-api.md)
for `ae_inputs`, output isolation and the remaining same-output overlap limitations.

## Optional provider adapters

Register an adapter when you need to add batch support to an existing AE2
provider. Declare OmniSequence as `compileOnly`. Load compatibility classes or
conditional Mixins that use this API only when `molecularmanipulator` is present.
The provider continues to register with AE2 as usual.

```java
OmniBatchProviderAdapterRegistry.register(
        "example:batch_machine", 100,
        (provider, pattern) -> provider instanceof ExampleProvider original
                && original.ownsPattern(pattern),
        (provider, pattern) -> new ExampleBatchAdapter((ExampleProvider) provider));
```

The adapter implements `OmniBatchCraftingProvider` and uses the same durable
prepare/commit/close protocol as a native implementation. The registry also offers
a provider-only `Predicate`/`Function` overload. Entries sort by descending
priority, then ascending ID; registering an existing ID replaces its entry.
`unregister(id)` removes it. Resolution tries matching factories until one returns
a non-null capability, then falls back to the original provider's direct interface.

Keep the original `ICraftingProvider` identity for AE pattern publication,
`isBusy()`, one-craft `pushPattern`, CPU selection, output accounting and
backpressure. The CPU calls only `prepareOmniBatch` on the resolved capability.
Post-accounting output adapters also receive the original provider. Never replace
that object in `CraftingService.getProviders` with a capability or proxy.

`supports(provider, pattern)` tests predicates without calling factories.
Predicates must be cheap, side-effect free and narrowly matched. A matching
predicate reserves the atomic batch path even if its factory temporarily returns
null; ordinary one-craft dispatch remains available. Registry recursion is guarded
per original provider. Predicate/factory failures cannot commit ownership and
cannot authorize non-atomic scaled dispatch; admissions still close after failure.

Factories run during capacity admission. Capabilities are resolved for the
current attempt. An empty registry uses the direct provider interface. The
existing API ABI remains version 1.

## Post-accounting output flush

An instant provider may finish a large aggregate before AE2 records its expected
outputs in the crafting job. If it delays new outputs until the next
tick, the job can pause whenever its `long`-sized `waitingFor` window fills.

Providers that own a durable output queue can implement
`OmniPostAccountingOutputProvider`. The CPU calls
`flushOutputsAfterCpuAccounting()` only after AE2 has recorded the accepted
aggregate's expected outputs. The method may retry delivery of already-produced
output, but must never execute the recipe again. Partial delivery must leave the
remainder in the provider's normal persistent retry queue.

Optional integrations that cannot modify the provider class may register an
adapter through `OmniPostAccountingOutputAdapterRegistry.register`. Registration
uses a stable namespaced string ID, a priority, a provider predicate, and a flush
consumer. Only the highest-priority matching adapter runs; if none matches, the
native provider protocol is used. The registry prevents recursive flushes for the
same provider identity. An adapter should be narrowly matched and should fail
closed when the target mod's internal queue layout is unknown.

Exact-count integrations must likewise preserve the `ICraftingProvider` object
published to AE2. Register an `OmniBigIntegerCraftingProvider` capability with
`OmniBigIntegerProviderAdapterRegistry.register` instead of replacing that
provider with a proxy in `CraftingService.getProviders`. The registry receives a
provider/pattern predicate and a capability factory. This keeps identity-based
registries in AE2 addons valid while letting the CPU resolve BigInteger support
only for the pattern being dispatched.

## Avoiding duplicate CPU batching

A provider mod that already redirects AE2's `CraftingCpuLogic` should bypass
its own material multiplier only for an Omni-managed CPU:

```java
if (OmniBatchCraftingApi.isOmniManagedCpu(this)) {
    return provider.pushPattern(pattern, oneCraftInputs);
}
return runTheModsOwnCpuBatching(...);
```

With provider-side admission, Omni handles extraction, AE power, task decrement,
expected-output accounting, rollback, and fair CPU scheduling.
This query is read-only and does not submit or migrate a crafting job.

Prefer a chainable Mixin Extras wrapper or an inject-and-cancel hook for CPU
compatibility. A hard `@Redirect` of the same `ICraftingProvider.pushPattern`
call can conflict with other mods before either marker check executes. If an
existing redirect must be retained, conditionally disable that redirect when
Omni is loaded and move the Omni-managed check to a compatible hook.

## Cycle transactions, byproducts and native doubling

An independent CPU acquires one
`AelisBatchExecutionContext.acquire(runtime, pattern.getDefinition(), source)`
before the first extraction. Use its inventory for the first craft, extra batch
inputs and refunds. Open `beginDispatch(actualInputs, actualCrafts)` before
provider delivery; call `accepted()` at durable ownership transfer. Closing a
rejected dispatch restores cycle accounting, while the CPU refunds still-owned
physical inputs. A null inventory declines this attempt; `maximumCrafts()` is a
phase bound and does not promise materials or queue capacity. Keep using the
first context after extraction.

Retain every expected output, including item/fluid byproducts. Unsupported
optimization or an unreadable external batch wrapper must preserve the complete
original task ledger and permit ordinary planning/dispatch. It must not turn a
secondary output into a planning exception or truncate work to a long preview.
Providers still enforce actual material ownership and capacity.

Query `AelisSmartDoublingApi.isExternallyManaged(pattern)` before adding a local
scale. Preserve native enabled patterns, existing external wrappers, provider
splits and the original-pattern remainder. An installed addon alone does not
enable this bypass. The query describes external management, not ownership or
exact execution support; keep AppliedEnhancements references in the appropriate
integration class.

## Exact-count capabilities

BigInteger providers and direct output receivers use a separate contract, described
in the [exact-count API](omni-exact-provider-api.md). They do not weaken this
SPI's prepare/commit ownership rules.
