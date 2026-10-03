# Exact-count provider API / 大数供应器接口

Current source: 2.0.8, Java 21, AE2 19.2.17+, revised AppliedEnhancements 1.1.1.
[API index](README.md) · [Atomic batch SPI](omni-batch-provider-api.md)

This capability tracks logical recipe/output amounts with `BigInteger`. It is
separate from the long-count `OmniBatchDelivery` SPI and from AppliedEnhancements
planning metadata. Register against the original AE `ICraftingProvider`; never
replace it in `CraftingService.getProviders`.

## Public contracts

| Type | Contract |
| --- | --- |
| `OmniBigIntegerCraftingProvider` | Capacity query and exact-count push using one extracted unit prototype |
| `OmniBigIntegerProviderAdapterRegistry` | Provider/pattern predicate and capability factory; priority, stable ID and native fallback |
| `OmniBigIntegerOutput` | Exact amount for one AEKey |
| `OmniBigIntegerBatchCallbacks` | Admission, actual output and cancellation callbacks |
| `OmniBigIntegerOutputReceiver` | Direct same-grid transfer to a live bound CPU with source debit |

```java
OmniBigIntegerProviderAdapterRegistry.register(
        "example:exact_machine", 100,
        (provider, pattern) -> provider instanceof ExampleProvider original
                && original.ownsPattern(pattern),
        (provider, pattern) -> new ExampleExactAdapter((ExampleProvider) provider));
```

Adapters and direct implementations share the same contract. Highest priority
resolves first; ties sort by ID. Null factory results allow another adapter or the
direct interface. Predicates run without factories for capability classification.

## Admission and ownership

`getMaximumBigIntegerCrafts(pattern, unitPrototype, requested)` must return a
currently admissible positive count no larger than requested. Zero/null declines.
The unit prototype is the real extracted one-craft input; a provider must validate
its keys and reserve every additional finite material required by the accepted
count. An exact logical count does not create physical input ownership.

`pushBigIntegerCraftingPattern` returns false without retaining the prototype.
True commits the agreed batch and requires durable input/output ownership.
`lastAcceptedBigIntegerCrafts()` may return a smaller exact positive count when
capacity changed; null guarantees the requested count was accepted. Validate
additional material, output capacity and durable state before returning success.

`usesNativeBigIntegerBatch()` returns true only for one atomic native admission.
That provider must not be driven through a repeated-long dispatch loop. Default
false retains compatibility with segmented implementations. The optional token
overload can bind completion callbacks; older implementations can use the original
method. Do not infer or cast an opaque token to internal implementation classes.

## Output and cancellation

Outputs preserve one exact amount per AEKey. `onAdmitted` reports planned outputs
after ownership commit; `onOutputs(List<OmniBigIntegerOutput>)` reports actual output
and retains the older total-only hook by default. Callbacks must be idempotent in
a durable integration and must not execute the recipe again.

For direct output delivery, call `transferOutput(sourceGrid, key, offered, debit)`
or the batch method only for the current same-grid binding. Transfer cannot be
accepted after cancellation/unbinding. The receiver invokes the source debit for
the amount transferred; debit durable source balances exactly once. A declined
portion remains owned by the source queue and follows its normal retry path.

The default batch method transfers each key separately, collects the accepted
amounts, then calls `debitSource` once with that map. It permits partial acceptance
and does not promise an atomic all-key transaction. Overrides must document their
stronger semantics; a debit callback must complete without throwing.

No general numeric ABI negotiation is exposed by these capability types. Bind to
the current documented method signatures and isolate optional class loading.
`OmniBatchCraftingApi.apiVersion()` negotiates the atomic batch SPI only.

## 中文

大数能力处理逻辑次数和按 AEKey 区分的精确产物数量。它不替代两阶段 long 批量投料，
也不自动提供规划所需的材料。注册器识别原始供应器和当前样板，返回独立能力；忙碌、
单份发配、队列映射与背压仍使用 AE 注册的对象。

容量返回值不得超过请求。提交前核对一份实际原料原型，并持有额外有限材料与产物
空间。拒绝不得留下原料，接受必须保存长期所有权；如果实际接受量变小，通过
`lastAcceptedBigIntegerCrafts()` 返回真实次数。原生一次原子提交才标记
`usesNativeBigIntegerBatch()`，不能把它当作普通 long 循环不断调用。

输出回调保留每种资源的数量，不能再次执行配方。直接回收只允许当前同网 CPU
绑定；成功转移按回调扣减源余额一次，剩余继续由来源保存。取消后不能接收迟到输出。
默认批量方法逐键转移，最后用已接收数量映射调用一次扣减；允许部分接收，不提供
所有资源同时提交的保证。扣减回调必须可靠完成且不能抛出异常。调用方负责权限、
线程、数据持久化和具体第三方机器的语义。
