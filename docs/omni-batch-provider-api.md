# Omni Batch Provider API v1

Available since OmniSequence: Transfinite 1.3.9.

Verified for OmniSequence 2.0.1-forge on Minecraft 1.20.1 / Forge / Java 17, with AE2 15.4.10–15.x
and the required AppliedEnhancements 1.0.6-forge. The runtime ABI remains **1**.
See the [API index](README.md) for the separate research and planner contracts.

Recompile integrations against these Forge artifacts; retaining ABI 1 does not
make NeoForge 1.21.1 binaries compatible with AE2 15 or Java 17. This update
does not change the provider ownership, cancellation or atomic-delivery contracts.

This SPI controls provider material delivery. AppliedEnhancements owns the AELIS
planning and cycle-execution APIs; importing Omni's internal planner/Mixin classes
is not a supported integration. Compile against the separate mod JARs without
embedding their classes.

This SPI is for AE2 machines that store encoded patterns and act as
`ICraftingProvider` implementations. A normal provider already works with AE2
one craft at a time. Implement this SPI only when the machine wants an
Omni-Computation Core to allocate several complete crafts and deliver them as
one atomic transaction.

The API contains no classes, Mod IDs, or reflection paths for a specific
integration. Any pattern-provider assembly can opt in.

## Public types

- `com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchProbe`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchRequest`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery`
- `com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi`
- `com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu`

Use `OmniBatchCraftingApi.apiVersion()` for a runtime ABI check. The matching
source constant is `API_VERSION`; do not rely on that compile-time constant for
runtime negotiation because Java may inline it.

## Provider integration

The provider continues to advertise patterns through AE2 and implements one
additional interface:

The example below shows the Omni-specific methods. Keep the existing AE2
`getAvailablePatterns()`, `pushPattern(...)` and `isBusy()` implementation;
`ownsPattern`, `completeCraftCapacity` and the queue helpers belong to your machine.

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

---

# 万物演算批量样板供应器 API v1

自 OmniSequence: Transfinite 1.3.9 起提供。

当前按 2.0.1-forge / Minecraft 1.20.1 / Forge / Java 17 核对，要求 AE2 15.4.10 至 15.x 和
AppliedEnhancements 1.0.6-forge；运行时 ABI 仍为 **1**。其他接口见 [API 索引](README.md)。
本 SPI 负责供应器材料交付；AELIS 规划及循环执行接口由 AppliedEnhancements 提供。
不要引用本模组已移除的规划器或内部 Mixin，也不要把两个模组的 API 类嵌入自己的 JAR。
调用方必须针对 Forge 前置重新编译；ABI v1 并不表示 1.21.1 NeoForge 二进制可直接运行。
本次界面修改不改变材料所有权、取消退款与原子交付契约。

此 SPI 面向“机器自身保存编码样板，并作为 AE2 `ICraftingProvider` 接单”的设备。
普通供应器本来就能按单份配方使用 AE2；只有希望由万物演算核心一次分配多份完整
材料时，才需要实现 `OmniBatchCraftingProvider`。

示例只展示 Omni 批量接口部分；机器仍需保留 AE2 的 `getAvailablePatterns()`、
`pushPattern(...)` 和 `isBusy()` 实现。样板归属、完整批次容量与持久队列辅助方法由接入机器提供。

接入流程为两阶段：

1. `prepareOmniBatch` 根据一份真实材料与请求上限，返回当前能原子接收的完整配方数；
2. Omni 抽取实际材料后调用 `commit`，供应器必须通过 delivery 明确接受或拒绝整批。

`OmniBatchProbe.Input` 只是首份配方实际抽取结果，用于估算容量；
`OmniBatchRequest.Input` 才是提交时具有所有权含义的最终 key 与整批总量。AE2 替代
输入可能让最终 key 或比例发生变化，不能用“probe × craftCount”校验最终交付。
运行时 ABI 检查应调用 `OmniBatchCraftingApi.apiVersion()`，不要依赖可能被 Java
内联的 `API_VERSION` 常量。

关键约束：

- `accept` 是唯一的所有权提交点。调用前，整批材料必须已经全部进入持久目标，或先
  写入供应器自身可保存的队列；
- `reject` 必须保证没有留下材料或不可撤销副作用，禁止部分接收；
- `CAPACITY_CHANGED` 只让该供应器/样板在本 tick 暂停批量探测，下个 tick 可重新
  admission；其他拒绝原因会让该组合在当前合成作业内退回 AE2 单份发配；
- 若供应器在 `accept` 后抛出运行时异常、链接故障或断言故障，Omni 仍按已接收处理，
  防止回灌造成复制；其他 `Error`（包括 JVM 致命故障）不会被吞掉；
- admission 一定会被关闭，`close` 只能释放临时预留，不能删除已接收材料；
- 有排队或容量已满时，`isBusy()` 必须如实阻止后续 AE2 发配；
- `RECHECK_NEXT_TICK` 与 `SATURATED` 只对当前供应器/样板施加本 tick 背压，不会
  停止无关样板或其他机器；
- 建议持久队列使用 `dispatchId` 去重；
- v1 只开放纯消耗材料的批量交付，返还容器、可复用工具和耐久变化配方继续安全地
  走单份路径。

公开输入记录使用 AEKey，可表示已注册附属类型；具体可处理类型由供应器决定。
数量沿用该 Key 的 AE 原生单位（物品个数、流体 mB 等）。数量为 long，
接收前必须检查每种 Key 的加法和乘法溢出；Long.MAX_VALUE 上限不会绕过实际原料、
供电、队列容量或产物计数限制。内置物质构筑井样板总成通过自身持久输入输出缓存实现
本接口，支持构筑井配方声明的所有已注册 AEKey 输入。接收与排队任务开工前检查
研究权限，开工时计算生产限制和加成。已开始加工的任务跨重载保留加工参数快照；
排队任务保存原料所有权与配方 ID，并使用当前配方定义。`ae_inputs`、产物隔离和
尚存的同产物重叠配方限制见[构筑井 API](matter-research-api.md)。

若第三方模组自己也修改了 AE2 CPU 的材料倍增逻辑，应在其 CPU Mixin 中调用
`OmniBatchCraftingApi.isOmniManagedCpu(this)`。返回 `true` 时跳过自身倍增，交给
Omni 调度；普通 CPU 仍可保留该模组原来的实现。

CPU 兼容 Hook 建议使用可链式的 Mixin Extras 包装，或 inject-and-cancel。若多个模组
对同一个 `ICraftingProvider.pushPattern` 调用使用硬 `@Redirect`，可能在执行上述判断
前就发生注入冲突；已有 Redirect 应在 Omni 加载时条件禁用，并把判断移到兼容 Hook。

API 中没有任何特定模组的类名或硬编码适配。第三方应把 OmniSequence 声明为
`compileOnly`，并仅在 Mod ID `molecularmanipulator` 已加载时启用兼容类或条件 Mixin。
