# ECO compatibility / ECO 兼容

Neo ECO AE Extension is optional. When installed, **21.2.9 or newer** is required on both client and server. The integration compiles against the unmodified 21.2.9 release; it is not bundled. ECO already orders itself after OmniSequence, so OmniSequence declares no reverse load-order edge.

Neo ECO AE Extension 为可选兼容；安装时客户端和服务端都必须使用 **21.2.9 或更新版本**。编译与隔离验证使用真实 21.2.9 发布 JAR，不将 ECO 打包进本模组。

## Execution contracts / 执行约定

| Direction | Execution |
| --- | --- |
| Omni CPU → ECO F/FX | Inventory-mode FastPath for ordinary, reusable and stateful recipes; native exact allocation for eligible FX virtual recipes; ordinary fallback when the provider declines optimization. |
| ECO CPU → molecular machines | Verified stateful batches retain selected component identities, tool durability, containers and task UUID. Outputs flush after CPU accounting. |
| ECO CPU → well Pattern Assembly | Atomic counted/exact admission into the durable queue, bounded by its per-resource long capacity and complete output representation. Actual execution retains the well's research, catalysts, energy and parallelism. |
| ECO plan → Omni CPU | Original task identities, exact counts, planned input allocations, phase/cycle ordering, protected startup stock and deferred material ownership are retained. |
| ECO parent order → Omni CPU | An accepted persistent parent reserves the CPU and original link while ECO asynchronously plans material/capacity-bounded children. Progress advances after actual child-output delivery. |

普通、复用工具和耐久变化配方通过 ECO 库存事务派发；符合 FX 虚拟执行条件的精确订单使用原生 BigInteger 入口。配方是否支持优化由实际能力决定，仍保留机器固有的电力、冷却、配方状态与线程约束。

物质构筑井的持久输入队列仍以每种资源的 `long` 数量持有实物；一次精确准入取完整可表示的最大批次，余量留在 CPU 精确账本中。此边界不增加固定的小批次或逐份派发限制，也不把溢出资源截断成 `long`。

ECO 阶段与循环计划保留执行顺序、启动材料保护、实际替代输入和任务 ID；精确任务量与等待产物使用独立账本。菜单确认只向当前选定且合格的 Omni CPU 提交同一份确认结果。缺料父订单保留原请求身份，等待资源与可执行的子计划；不会将未解或被阻止的图当作有效执行计划。

## Ownership and persistence / 所有权与存档

Preparation moves no resources. A confirmed rejection restores CPU materials and retains prepaid energy as CPU credit. Acceptance advances task progress and waiting outputs once. Provider-owned outputs are routed by job UUID; simulation never mutates either ledger. Final products honor requester/network acceptance and retain undelivered physical stock.

If provider acceptance or a downstream delivery becomes uncertain, the CPU persists the complete intent and pauses. It must not automatically replay the batch or refund resources that may already belong to a machine. Reload failures retain the original state for recovery rather than discarding owned materials. Ordinary cancellation recovers only the canceled job's remaining inputs and preserves completed products.

准备阶段不移动物料；明确拒收时恢复材料，已支付电力保留为 CPU 信用。接收、次数扣账与预计产物只结算一次。取消与恢复按任务 UUID 隔离；如果接收结果无法确定，则持久保留投入和事务状态并暂停，禁止自动退款或重放。

## Verification / 验证

Use the project's Gradle wrapper with Java 21. The ECO suite stages selected dependencies into a disposable directory under `build/`; it never writes player saves or installed mod JARs. See [GameTest instructions](../tools/gametest/README.md) for runtime setup. Unit tests cover material leases, exact output settlement, tool state, plan codecs, phase progress, parent orders and optional-mod class loading. Real GameTests additionally exercise transformed CPUs, actual providers, cancellation, reload and menu integration.

Verified against ECO 21.2.9, AE2 19.2.17, Applied Enhancements 1.1.4, Thunderbolt 2.0.0-beta.4 and LDLib2 2.2.40: **480 unit tests and 40 required GameTests passed**. Five hardware tests construct native F4/F9 hosts, including the full FX topology of eight F9 hosts and 88 workers. A native FX thread accepts `9,223,372,036,854,776,833` crafts in one exact batch; completion, worker reload, coolant blocking and cancellation conserve the full quantities. Cold recipe verification also advances exact and phase accounting exactly once. These tests verify execution and ownership using controlled finite ME storage and creative power; they do not measure survival throughput.

上述整合包版本组合已通过 **480 项单元测试和 40 项必需游戏测试**。真实 F/FX 结构验证覆盖普通容量、超 `long` 单次批量、冷却液限制、首次配方验证、任务 UUID、工作单元重载和取消回料；保留原生能源与结构条件。

Compatibility adapters use ECO's own runtime and codec through optional mixins and public API calls. They do not embed ECO source or replace its JAR. Future ECO releases may change internal contracts; the declared minimum version is not a guarantee of compatibility with every future implementation.
