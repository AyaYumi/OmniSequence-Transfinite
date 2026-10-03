# 万物演算批量样板供应器 API v1

对应 2.0.7-forge，Minecraft 1.20.1 / Java 17 / AE2 15.4.10 / UELM 15.5.4 / AppliedEnhancements 1.1.0-forge。

[English](omni-batch-provider-api.md) · [API 索引](README.md)

自 OmniSequence: Transfinite 1.3.9 起提供。

运行时 ABI 仍为 **1**。
本 SPI 负责供应器材料交付；AELIS 规划及循环执行接口由 AppliedEnhancements 提供。
不要引用本模组已移除的规划器或内部 Mixin，也不要把两个模组的 API 类嵌入自己的 JAR。

此 SPI 面向“机器自身保存编码样板，并作为 AE2 `ICraftingProvider` 接单”的设备。
普通供应器本来就能按单份配方使用 AE2；只有希望由万物演算核心或超限算枢一次分配多份完整
材料时，才需要实现 `OmniBatchCraftingProvider`。

Advanced AE 量子 CPU 也通过可选兼容接入该协议。单方块超限算枢没有新增供应器 ABI 或资源格式。

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
尚存的同产物重叠配方限制见[构筑井 API](matter-research-api.zh-CN.md)。

## 可选供应器适配

无需修改第三方供应器类或让其直接实现 Omni 接口，兼容模组可通过
`OmniBatchProviderAdapterRegistry.register` 注册整批能力。兼容类应仅在
`molecularmanipulator` 已加载时启用，依赖使用 `compileOnly`。示例：

```java
OmniBatchProviderAdapterRegistry.register(
        "example:batch_machine", 100,
        (provider, pattern) -> provider instanceof ExampleProvider original
                && original.ownsPattern(pattern),
        (provider, pattern) -> new ExampleBatchAdapter((ExampleProvider) provider));
```

适配器实现 `OmniBatchCraftingProvider`，并遵守同样的 prepare/commit/close 和
持久材料归属约束。也支持仅按供应器匹配的 Predicate/Function 重载。优先级高的先
匹配，同优先级按 ID 排序；重复 ID 替换原注册，`unregister(id)` 移除注册。
依次尝试匹配工厂，取得首个非空能力；都未返回能力时回退到原供应器的直接接口。

AE 样板发布、忙碌判断、单份 pushPattern、CPU 选择、产物记账和背压始终使用 AE
注册的原始 ICraftingProvider；只对适配能力调用 prepareOmniBatch。记账后排空
注册器也接收原始供应器。不要在 CraftingService.getProviders 中用代理替换它。

supports(provider, pattern) 只检查识别条件，不创建适配器。识别条件必须轻量、
无副作用且精确匹配；命中后即保留原子批量路径，即使工厂暂时返回 null，也不会
退入不安全的材料倍增；普通单份发配仍可使用。相同供应器有递归保护，适配识别或
工厂异常不会提交材料所有权，也不会放行非原子倍增；已取得的 admission 仍会关闭。

工厂只在实际批量尝试时执行，能力不跨尝试缓存。没有注册时直接使用原生接口，
注册变更会使本 tick 的供应器拓扑缓存失效。AE 与 AdvancedAE 均接入该能力，
原有接口保留，API ABI 仍为 1。

## CPU 记账后同步排空

瞬时加工供应器可能在 AE2 将预计产物登记进合成任务前就完成整批加工。如果供应器
刻意把本 tick 新产物延迟到下一 tick，任务的 `long` 容量 `waitingFor` 窗口填满时
就会产生停顿。

拥有持久输出队列的供应器可直接实现 `OmniPostAccountingOutputProvider`。CPU 只有在
AE2 完成已接收批次的预计产物记账后，才调用
`flushOutputsAfterCpuAccounting()`。该方法只能重试交付已经产出的内容，不能再次执行
配方；若 ME 网络只接受一部分，剩余量必须继续保存在供应器原有的持久重试队列中。

无法修改供应器类的可选兼容模组，可以通过
`OmniPostAccountingOutputAdapterRegistry.register` 注册适配器。注册参数包括稳定的
命名空间 ID、优先级、供应器识别条件和排空函数。系统只执行优先级最高的首个匹配项；
没有适配器时才调用机器原生协议。同一供应器身份不能递归排空。适配器必须精确识别
目标机器；无法确认第三方模组内部队列结构时应拒绝匹配并保留原本的下一 tick 重试。

BigInteger 精确数量兼容也必须保留 AE2 发布的原始 `ICraftingProvider` 对象。第三方应
通过 `OmniBigIntegerProviderAdapterRegistry.register` 注册“供应器/样板识别条件”和
能力工厂，不要在 `CraftingService.getProviders` 中用代理替换供应器。这样既可按具体
样板解析大数能力，又不会破坏 AE2 附属模组按对象身份保存的适配器映射。

若第三方模组自己也修改了 AE2 CPU 的材料倍增逻辑，应在其 CPU Mixin 中调用
`OmniBatchCraftingApi.isOmniManagedCpu(this)`。返回 `true` 时跳过自身倍增，交给
Omni 调度；普通 CPU 仍可保留该模组原来的实现。

CPU 兼容 Hook 建议使用可链式的 Mixin Extras 包装，或 inject-and-cancel。若多个模组
对同一个 `ICraftingProvider.pushPattern` 调用使用硬 `@Redirect`，可能在执行上述判断
前就发生注入冲突；已有 Redirect 应在 Omni 加载时条件禁用，并把判断移到兼容 Hook。

API 中没有任何特定模组的类名或硬编码适配。第三方应把 OmniSequence 声明为
`compileOnly`，并仅在 Mod ID `molecularmanipulator` 已加载时启用兼容类或条件 Mixin。

## 大数能力

BigInteger 供应器和直接产物接收器采用独立契约，详见[大数 API](omni-exact-provider-api.md)。
这些能力不会绕过本接口的两阶段材料归属规则。
