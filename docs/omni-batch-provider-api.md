# Omni Batch Provider API v1

Available since OmniSequence: Transfinite 1.3.9.

Current for OmniSequence 2.0.7-forge on Minecraft 1.20.1 / Java 17, with AE2 15.4.10 / UELM 15.5.4
and the required AppliedEnhancements 1.1.0-forge. The runtime ABI remains **1**.
Other languages: [中文版](omni-batch-provider-api.zh-CN.md).
See the [API index](README.md) for the separate research and planner contracts.

This SPI controls provider material delivery. AppliedEnhancements owns the AELIS
planning and cycle-execution APIs; importing Omni's internal planner/Mixin classes
is not a supported integration. Compile against the separate mod JARs without
embedding their classes.

This SPI is for AE2 machines that store encoded patterns and act as
`ICraftingProvider` implementations. A normal provider already works with AE2
one craft at a time. Implement this SPI only when the machine wants an
Omni-Computation Core or Transfinite Compute Nexus to allocate several complete crafts and deliver them as
one atomic transaction.

Advanced AE quantum CPUs also use this provider contract through the optional
integration. The single-block nexus adds no new provider ABI or resource format.

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
additional interface:

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

Both input lists preserve the original pattern-input slot, and multiple
substituted keys in one slot remain separate entries. They intentionally use
different public types: `OmniBatchProbe.Input` is only the actual selection for
the first craft and is a capacity hint. `OmniBatchRequest.Input` is the
authoritative key and total amount delivered at commit time. AE2 substitutions
may make its keys or ratios differ from `probe * craftCount`; never validate the
delivery with that multiplication.

The request also carries a unique dispatch ID, the current AE2 crafting-job ID
when available, the exact accepted craft count, and the authoritative total
expected outputs. Inventory or AE power may reduce the final request to any
count from two through `admission.maxCrafts()`; an admission must accept that
whole range or reject atomically at commit time.

## Ownership contract

- `prepareOmniBatch` may only inspect state or create a reversible reservation.
- `commit` runs synchronously on the server thread and must call exactly one of
  `accept` or `reject` before returning.
- `accept` is the ownership commit point. Before calling it, every input must
  already be in a durable target or in the provider's persisted queue.
- `reject` means the provider retained no material and made no irreversible
  change. Partial acceptance is forbidden.
- `CAPACITY_CHANGED` suppresses this provider/pattern endpoint for the rest of
  the current tick and permits a fresh admission next tick. Other rejection
  reasons disable batch delivery for that provider/pattern for the current
  crafting job; normal one-craft AE2 dispatch remains available.
- If provider code raises a runtime exception, linkage failure, or assertion
  failure after calling `accept`, Omni still treats the delivery as accepted so
  the CPU cannot duplicate the materials by reinjecting them. Other `Error`
  subclasses, including JVM-fatal failures, are never swallowed.
- Returning without a decision is a rejection. A delivery cannot be completed
  twice or from another thread.
- Omni always closes the admission. `close` may release a reservation, but must
  never discard an accepted batch.
- A queued or saturated provider must keep `isBusy()` truthful until it can
  accept another AE2 dispatch. Persist queue changes before returning.
- `RECHECK_NEXT_TICK` and `SATURATED` backpressure only suppress another batch
  probe for the same provider/pattern during the current tick. They do not
  pause unrelated patterns or providers.
- Use `dispatchId` for idempotency if the provider has a durable work queue.

API v1 deliberately batches only purely consumable inputs. Recipes with
returned containers, reusable tools, or durability transitions keep the safe
one-craft path unless an existing internal Omni machine handles them.

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
starts; started work retains its processing snapshot across reloads. Queued work
retains material ownership and recipe IDs while using current definitions. Its
UI buffer size is not an unconditional batch limit. See the [well API](matter-research-api.md)
for `ae_inputs`, output isolation and the remaining same-output overlap limitations.

## Optional provider adapters

An optional integration can register a batch capability without adding an Omni
interface to the target mod's provider class. Load the compatibility registration
class only when `molecularmanipulator` is present; use a `compileOnly` dependency.
The target provider itself continues to register with AE2 as usual.

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

Factories run during an actual batch attempt, not every topology query. No
capability is cached across attempts. An empty registry uses the direct-interface
fast path, and registration changes invalidate the CPU's per-tick topology cache.
This registry is additive; the existing API ABI remains version 1.

## Post-accounting output flush

An instant provider may finish a large aggregate before AE2 records its expected
outputs in the crafting job. If it deliberately delays new outputs until the next
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

The provider-side admission above then lets Omni perform extraction, AE power,
task decrement, expected-output accounting, rollback, and fair CPU scheduling.
This query is read-only and does not submit or migrate a crafting job.

Prefer a chainable Mixin Extras wrapper or an inject-and-cancel hook for CPU
compatibility. A hard `@Redirect` of the same `ICraftingProvider.pushPattern`
call can conflict with other mods before either marker check executes. If an
existing redirect must be retained, conditionally disable that redirect when
Omni is loaded and move the Omni-managed check to a compatible hook.

For an optional dependency, keep references to this API in a compatibility
class or conditional Mixin that is loaded only when Mod ID
`molecularmanipulator` is present. Compile against OmniSequence as
`compileOnly`; do not embed its API classes.


## Exact-count capabilities

BigInteger providers and direct output receivers use a separate contract, described
in the [exact-count API](omni-exact-provider-api.md). They do not weaken this
SPI's prepare/commit ownership rules.
