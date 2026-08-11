# Omni Batch Provider API v1

Available in OmniSequence: Transfinite `1.3.8-forge` for Forge 1.20.1,
with the same API v1 contract as the 1.21.1 line.

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
substituted keys in one slot remain separate entries. `OmniBatchProbe.Input` is
only the actual selection for the first craft and is a capacity hint.
`OmniBatchRequest.Input` is the authoritative key and total amount delivered at
commit time. AE2 substitutions may make its keys or ratios differ from
`probe * craftCount`; never validate the delivery with that multiplication.

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
  the CPU cannot duplicate the materials by reinjecting them. JVM-fatal errors
  are never swallowed.
- Returning without a decision is a rejection. A delivery cannot be completed
  twice or from another thread.
- Omni always closes the admission. `close` may release a reservation, but must
  never discard an accepted batch.
- A queued or saturated provider must keep `isBusy()` truthful until it can
  accept another AE2 dispatch. Persist queue changes before returning.
- `RECHECK_NEXT_TICK` and `SATURATED` backpressure only suppress another batch
  probe for the same provider/pattern during the current tick.
- Use `dispatchId` for idempotency if the provider has a durable work queue.
- Keep provider capacity arithmetic saturating at `Long.MAX_VALUE` (or derive
  craft limits with division); never allow a multiplication overflow to wrap
  into a smaller or negative admission.

API v1 deliberately batches only purely consumable inputs. Recipes with
returned containers, reusable tools, or durability transitions keep the safe
one-craft path unless an existing internal Omni machine handles them.

## Avoiding duplicate CPU batching

A provider mod that also modifies AE2's `CraftingCpuLogic` should bypass its
own material multiplier only for an Omni-managed CPU:

```java
if (OmniBatchCraftingApi.isOmniManagedCpu(this)) {
    return provider.pushPattern(pattern, oneCraftInputs);
}
return runTheModsOwnCpuBatching(...);
```

Prefer a chainable Mixin Extras wrapper or an inject-and-cancel hook. Two hard
`@Redirect` hooks targeting the same `ICraftingProvider.pushPattern` call may
conflict before either marker check executes. Optional integrations should
compile against OmniSequence as `compileOnly`, avoid embedding the API classes,
and load only when Mod ID `molecularmanipulator` is present.

---

# 万物演算批量样板供应器 API v1

Forge 1.20.1 的 `1.3.8-forge` 提供与 1.21.1 分支相同的 API v1 ABI。
该 SPI 面向“机器自身保存编码样板，并作为 AE2 `ICraftingProvider` 接单”的设备。
普通供应器本来就能按单份配方使用 AE2；只有希望由万物演算核心一次分配多份完整
材料时，才需要实现 `OmniBatchCraftingProvider`。

接入流程为两阶段：

1. `prepareOmniBatch` 根据一份真实材料与请求上限，返回当前能原子接收的完整配方数；
2. Omni 抽取实际材料后调用 `commit`，供应器必须明确接受或拒绝整批。

`OmniBatchProbe.Input` 只是首份配方实际抽取结果，用于估算容量；
`OmniBatchRequest.Input` 才是提交时具有所有权含义的最终 key 与整批总量。AE2 替代
输入可能让最终 key 或比例发生变化，不能用“probe × craftCount”校验最终交付。
运行时 ABI 检查应调用 `OmniBatchCraftingApi.apiVersion()`，不要依赖可能被 Java
内联的 `API_VERSION` 常量。

关键约束：

- `accept` 是唯一的所有权提交点，调用前整批材料必须全部进入持久目标或持久队列；
- `reject` 必须保证没有留下材料或不可撤销副作用，禁止部分接收；
- `CAPACITY_CHANGED` 只暂停该供应器/样板本 tick 的批量探测，其他拒绝原因令该组合
  在当前合成作业内退回 AE2 单份发配；
- admission 一定会关闭，`close` 只能释放临时预留，不能删除已接收材料；
- 有排队或容量已满时，`isBusy()` 必须如实阻止后续 AE2 发配；
- 建议持久队列使用 `dispatchId` 去重；
- 容量运算必须钳制在 `Long.MAX_VALUE`，或优先用除法反推份数，禁止乘法溢出回绕；
- v1 只开放纯消耗材料的批量交付，返还容器、可复用工具和耐久变化配方走安全路径。

若第三方模组也修改了 AE2 CPU 材料倍增，应调用
`OmniBatchCraftingApi.isOmniManagedCpu(this)`；返回 `true` 时跳过自身倍增，交给
Omni 调度。第三方应把 OmniSequence 声明为 `compileOnly`，且不要内嵌 API 类。
