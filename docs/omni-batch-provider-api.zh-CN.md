# 万物演算批量样板供应器 API v1

模组开发者可实现此接口，让 AE2 样板供应器一次接收多份完整配方，由 Omni 分配并交付整批材料。
普通 AE2 供应器无需实现它也能正常接单。玩家搭建与操作见[玩家指南](../README.zh-CN.md)或游戏内 G 指南。

对应 21.3.0.5，Minecraft 1.21.1 / Java 21 / AE2 19.2.18 / AppliedEnhancements 1.1.2+（编译 API 1.1.4）。

[English](omni-batch-provider-api.md) · [API 索引](README.md)

自 OmniSequence: Transfinite 1.3.9 起提供。

运行时 ABI 为 1。AELIS 规划和循环执行使用 AppliedEnhancements 的公开接口。

此 SPI 面向自身保存编码样板、作为 AE2 `ICraftingProvider` 接单的设备。
希望由万物演算核心或超限算枢一次分配多份完整材料时，实现 `OmniBatchCraftingProvider`。

L7 起，普通 AE2 样板供应器能为没有返还物、且输入键与产物不重合的标准处理样板合批。
供应器须在线、发送队列为空，并关闭阻挡和合成锁。Omni 比较可用输入面，将整批交给容量最大的一个面。遇到能装下全部请求的面即可停止查找。
单键使用目标的模拟插入。多键需要可验证的库存布局，共用空槽只占一次，独立通道各自计算容量。
配置过的 ME 接口按输入顺序分配过滤槽和共享槽。ME 箱里的普通存储元件按整批共用的字节和新增类型收费。带虚空卡的元件会把已销毁的物品也计为接收，容量合批会跳过这个目标。对于 MEK 加压反应室，物品、流体和化学品必须从同一面进入对应的输入槽罐，并匹配同一个反应配方。

提交时复核选定面。已加载的供应器如果还有材料排队送往同一库存，该面暂缓接新批次，
双箱两半按同一库存处理。机器只收到一部分时，剩余材料保存在供应器队列中，继续从原方向发送。

L0～L6 使用 AE2 原生逐份推送和滚动操作窗口。L7～L9 按订单余量、原料、供电、当前配方阶段和数量类型可表示的范围请求整批。普通供应器用这个数量探测目标输入面，一次接下能放入的完整配方。目标装得下整单时，任一高档都能一次提交整单。各订单共享控制器的派发工作额度和服务器的时间窗口。

无法验证整组容量的供应器需要通过适配器合批。缺少适配器时，AE2 可在全服回退额度内逐份投料。原料和电力能支持多份、供应器却只能逐份接单时，服务器日志会记录这张订单首次成功回退的产物和供应器类型。带返还物的配方也可使用原生推送。AELIS 循环共享种子库存。

分子构序重写阵列、构序阵列和装配矩阵构序重写核心能自行验证返还物。每份消耗一个满桶时，整批按实际份数扣满桶、登记空桶。对可复用催化剂或确定性磨损工具，普通订单按整批实际占用的工具扣料，并检查成品、最终剩余工具和取消退款的缓冲空间。需要逐种验证工具磨损状态时，机器可以分多个 tick 核对实际配方。验证期间材料留在 CPU，整批通过后才交给机器。由循环或阶段计划指定的输入继续服从该阶段的材料与种子分配。

NeoECO 总线给出可用容量时，普通配方可直接按报价合批。端点附带状态计算器时，CPU 先核对它给出的份数、整批耗材和返还物，再提交相同的一批。支持精确输入的端点可接收超过 `long` 的同键原料总量。NeoECO 使用 `long` 的份数和产物字段仍按各字段能表示的数量提交。

物质构筑井样板总成更新队列时提交材料所有权。随后标记存档状态的通知抛出运行时异常时，
仍保留已接收结果，让 ECO 对已入队的整批材料只结算一次。

控制器每 tick 按有效等级选定推送路径。切档后，未派发的份数按新档位执行，供应器继续交付已接收的批次。

万物演算核心和超限算枢使用同一供应器 ABI。独立的 Advanced AE 量子 CPU 由 Advanced AE 及其兼容层执行。

## 接入步骤与材料归属

接入流程为两阶段（完整 Java 实现片段见[英文版](omni-batch-provider-api.md#provider-integration)；
示例中的库存检查、容量与持久队列辅助方法由接入机器实现）：

1. `prepareOmniBatch` 根据一份真实材料与请求上限，返回当前能原子接收的完整配方数；
2. Omni 抽取实际材料后调用 `commit`，供应器必须通过 delivery 明确接受或拒绝整批。

`OmniBatchProbe.Input` 记录当前一份配方的原料选择，只用于查询容量。CPU 按该选择预留整批材料，提交时以 `OmniBatchRequest.Input` 的 key 和总量为准。
运行时 ABI 检查应调用 `OmniBatchCraftingApi.apiVersion()`，不要依赖可能被 Java
内联的 `API_VERSION` 常量。

材料归属规则：

- `accept` 是唯一的所有权提交点。调用前，整批材料必须已经全部进入持久目标，或先
  写入供应器自身可保存的队列；
- `reject` 必须保证没有留下材料或不可撤销副作用，禁止部分接收；
- `CAPACITY_CHANGED` 让该供应器与样板等到下个 tick 再查询容量。其他明确拒收会先退回预留，再按当 tick 的额度尝试一次单份推送。
- 若供应器在 `accept` 后抛出运行时异常、链接故障或断言故障，Omni 仍按已接收处理，
  防止回灌造成复制；其他 `Error`（包括 JVM 致命故障）不会被吞掉；
- 提交结束后若供应器仍未明确接受或拒绝，CPU 会保留预留记录并暂停该订单，避免重复发料。
- admission 一定会被关闭，`close` 只能释放临时预留，不能删除已接收材料；
- 有排队或容量已满时，`isBusy()` 必须如实阻止后续 AE2 发配；
- `RECHECK_NEXT_TICK` 与 `SATURATED` 只对当前供应器/样板施加本 tick 背压，不会
  停止无关样板或其他机器；
- 建议持久队列使用 `dispatchId` 去重；
- v1 只开放纯消耗材料的批量交付，返还容器、可复用工具和耐久变化配方继续安全地
  走单份路径；内置机器的专门处理不会扩展公开 v1 的材料归属契约。

公开输入记录使用 AEKey，可表示已注册附属类型；具体可处理类型由供应器决定。
数量沿用该 Key 的 AE 原生单位（物品个数、流体 mB 等）。数量为 long，
接收前必须检查每种 Key 的加法和乘法溢出；Long.MAX_VALUE 上限不会绕过实际原料、
供电、队列容量或产物计数限制。

内置物质构筑井样板总成通过自身持久输入输出缓存实现本接口，支持构筑井配方声明的
所有已注册 AEKey 输入。接收与排队任务开工前检查研究权限，开工时计算生产限制和加成。
已开始加工的任务跨重载保留原料、产物与功率快照；耗时随当前引擎收益调整，进度按比例换算。
排队任务保存原料所有权与配方 ID，使用当前配方定义。`ae_inputs`、产物隔离和
尚存的同产物重叠配方限制见[构筑井 API](matter-research-api.zh-CN.md)。

## 可选供应器适配

通过 `OmniBatchProviderAdapterRegistry.register` 为现有 AE2 供应器注册整批能力，
无需修改供应器类。把 OmniSequence 声明为 `compileOnly`，只在 `molecularmanipulator` 已加载时
启用引用此接口的兼容类或条件 Mixin。

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

工厂在查询当前容量时执行，能力不跨尝试缓存。没有注册时直接使用原供应器的接口，API ABI 仍为 1。

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
能力工厂，不要在 `CraftingService.getProviders` 中用代理替换供应器。这样可按具体
样板解析大数能力，并保留 AE2 附属模组按对象身份保存的适配器映射。

若第三方模组自己也修改了 AE2 CPU 的材料倍增逻辑，应在其 CPU Mixin 中调用
`OmniBatchCraftingApi.isOmniManagedCpu(this)`。返回 `true` 时跳过自身倍增，交给
Omni 调度；普通 CPU 仍可保留该模组原来的实现。

CPU 兼容 Hook 建议使用可链式的 Mixin Extras 包装，或 inject-and-cancel。若多个模组
对同一个 `ICraftingProvider.pushPattern` 调用使用硬 `@Redirect`，可能在执行上述判断
前就发生注入冲突；已有 Redirect 应在 Omni 加载时条件禁用，并把判断移到兼容 Hook。

## 循环事务、副产物与原生倍增

独立 CPU 在首次抽料前调用
`AelisBatchExecutionContext.acquire(runtime, pattern.getDefinition(), source)`，
首份、追加批量和退款始终使用同一个库存视图。交付前打开
`beginDispatch(actualInputs, actualCrafts)`，持久所有权移交时确认 `accepted()`。
拒绝后关闭事务会恢复循环记账；CPU 仍负责退回自己持有的实物。库存为 null 时
本次不可发配，`maximumCrafts()` 只表示当前阶段上限，不保证材料或队列容量。
首次抽料后继续使用先前获取的上下文。

完整记录主产物与所有物品、流体副产物。优化或外部批量包装不可识别时，保留
原始完整任务账本并回退普通规划/发配，不能因副产物直接中断规划，也不能将
long 预览窗口当成完整订单。供应器仍须满足真实材料、容量和持久所有权契约。

通过 `AelisSmartDoublingApi.isExternallyManaged(pattern)` 判断是否已由外部管理倍率。
保留原生样板包装、供应器分配与原样板尾数，避免再次倍增；仅安装附属不会让全部样板
绕过本地处理。该查询不代表所有权或大数执行能力，相关类型应放在对应的兼容类中加载。

## 大数能力

BigInteger 供应器和直接产物接收器采用独立契约，详见[大数 API](omni-exact-provider-api.md)。
这些能力不会绕过本接口的两阶段材料归属规则。
