# Remaining interceptions / 两模组剩余拦截清单

Audit date: 2026-10-04. Covers Applied Enhancements 1.1.0-forge / 1.1.1 and OmniSequence Transfinite 2.0.7-forge / 2.0.8. IDs group checks by one user decision, rather than listing every repeated conditional. Selected A02/A03/A07/A11/A19 rejection paths have now been removed at the user's request; other checks remain. English release changelogs accompany the builds; the detailed decision table below is in Chinese.

The removed Applied native 64-bit arithmetic rejection is **A00 (removed)**. Five rejection Mixins, the exception/helper and its rejection message are gone. Exact native task accumulation was retained. Omni's separate **O01 shared KeyCounter saturation remains**. A02/A03/A07/A11/A19 no longer reject requests; A11 now repairs projections or restores original tasks; A20/O34 cancel invalid running states. These are not the deleted native overflow exception.

## 最先确认的项目

| 编号 | 日常表现 |
| --- | --- |
| A03 | 已按要求移除拒绝，详情见当前规则表 |
| A19 | 已按要求移除拒绝，详情见当前规则表 |
| A07 | 已按要求移除拒绝，详情见当前规则表 |
| A11 / O08 | A11 已改恢复原计划；O08 异常规范化暂停仍保留 |
| A08 / O14 | 材料为已确认／研究订单预留，其他订单暂时不能抽取 |
| A20 / O34 | 循环／精确运行状态损坏或初始化失败，主动取消任务 |
| O01 | 共享 AE 计数超过 long 时饱和保留上限，不拒绝下单 |
| O29–O31 / A15–A17 / C03 | TPS 预算、缓存和日志限流，按此前要求保留 |

## 阅读和确认方法

本清单保留 **60 组编号，其中 5 组拒绝已按要求移除**，覆盖两个模组的服务端玩法、AE Mixin、批量／精确 API、网络载荷、持久化和 TPS 控制。重复的空值检查、同一机制的多条分支合为一个编号；界面排版、格式化和普通 setter 没有逐行当成拦截。源码链接分别对应两个分支，便于核对，分支链接会随后续更新移动。

- **拒绝**：本次操作不成立或返回异常。
- **回退**：不使用这条优化路径，改由原生／普通路径处理；不代表整单被取消。
- **等待／背压**：材料、能量、容量或工作预算不足，后续可继续。
- **过滤／饱和／展示**：改变允许对象、数值投影或客户端展示，不是下单拒绝。

可回复“删除 O08；保留 A08、O01；O29 调成……”。A02、A03、A07、A11、A19 已按选择移除拒绝；其他未选项保持当前行为。TPS 预算、缓存和日志限流按你之前的要求仍保留。

**已删除 A00**：Applied 原生 AE2 64 位规划算术溢出拒绝。没有开关，因为代码已删除；不会再生成截图里的 `UnsafeNativeCraftingRequestException`。A19 拒绝也已移除；A18 的投影和 O01 的饱和仍在，取消 A00 拒绝并不使所有外部原生 long 算术自动具备任意精度。

## Applied Enhancements

| 编号 | 检查 | 触发条件与作用 | 结果 | 开关／默认值 |
| --- | --- | --- | --- | --- |
| A01 | 订单模式与正数输入 | 关闭增强模式时，增强入口不接受超过原生菜单范围的订单；LONG_MAX 接 long，BIG_INTEGER 才接超 long；零、负数不下单。 | 拒绝增强入口／委托原生 | `crafting.max_crafting_order_amount=DISABLED` |
| A02 | 已移除：精确订单拒绝 | 不再限制 256 位、拒绝 CRAFT_LESS 或被大整数偏好开关阻止；非正数按空订单，输入仍按 A01 订单模式，网络传输有 1,048,576 字符边界。 | 不再因此抛异常阻止；精确 API 可继续 | `显式精确请求独立于 enable_big_integer_planning` |
| A03 | 已移除：有序候选／重放拒绝 | 优化不能批量处理时允许回退原生，不再因百万需求或超过 64 步而抛终止异常；64 步仍用于选择优化重放策略。 | 原生继续计算 | `拒绝代码已删除；A04/A15 优化预算保留` |
| A04 | 原生边界、重放与递归限额 | AELIS 内部原生边界上限 8,192；模拟有序重放 64 步；事务递归深度 256。达到限额停止该优化路径。 | 优化回退；不再以 A03/A19 终止 | `固定；AELIS 路径` |
| A05 | 配方语义与内部算术检查 | 空／无效输入输出、动态布局、候选身份改变、数量反馈不匹配、容器返还／工具状态无法证明，以及优化内部 long 乘加超范围，均不能按原聚合结果继续。 | 回退原生／当前候选失败；部分 CraftBranchFailure 直接报告失败 | `没有单独总开关；自动 AELIS 默认 false` |
| A06 | 循环启动料和种子保护 | 循环执行只能使用已证明的启动料；保护最低种子量，不允许其他阶段或非当前循环步骤抢用；不足时等待。 | 限制抽取／延后派发 | `cycle_solver.seed_policy=PRESERVE_MINIMUM；另一值 MAX_THROUGHPUT` |
| A07 | 已移除：指定 CPU 循环能力拒绝 | Applied 不再因指定 CPU 未声明循环能力而返回 noSuitableCpu；CPU 自身在线、忙碌和容量结果仍有效。 | 交由 AE/目标 CPU 提交 | `能力查询保留，拒绝入口删除` |
| A08 | 手工确认材料预留锁 | 其他打开的确认菜单已预留的有限材料，从规划库存扣除并限制实际／模拟抽取；抢锁失败会重新规划。 | 限制其他请求取料／重算 | `跟随 crafting.aelis.enable_automatic_planner=false` |
| A09 | 无限来源识别和回流处理 | 只有明确标记且当前可访问的无限来源才无限供给；普通 Long.MAX_VALUE 不自动当无限。命中无限键时绕过有限抽取、忽略模拟回流，并单独合并精确消耗。 | 覆盖模拟取料／取消模拟插入；不拒绝整张订单 | `storage.infinite.enable_listing_limit_bypass=false` |
| A10 | 原生智能倍增跳过本地倍率 | 识别 EAEP eap$allowScaling 开关、已知 EAEP／无用之物倍率包装和原生供应器；已托管任务不再套 Applied 的倍率接口。未知第三方开关并非自动识别。 | 跳过本地改写／保留原生任务 | `已启用兼容行为；无开关` |
| A11 | 已移除：倍率元数据拒绝 | 可读倍率数量不一致时用精确需求修复批次和尾数；未知、非法、递归、溢出或不可读倍率改写恢复原始精确任务；可选改写调用失败返回原计划。 | 恢复／修复后继续，不抛本组拒绝 | `按类缓存与原生倍增绕过仍保留` |
| A12 | 无任务确认页自动关闭 | 确认页面连续 100 tick 没有 job、result 或 plan 时返回上页，避免永久显示计算中；关页或新下单取消旧计算。 | 关闭界面／取消旧请求；不是有效研究强制超时 | `固定 100 tick` |
| A13 | 终端批量移动样板检查 | 来源最多 512、目标最多 128；来源必须存在且为一张已编码样板；不能同源同目标；无效样板跳过；目标空间不足、预检后拒收则整次回滚。 | 拒绝移动／跳过无效项／回滚 | `固定` |
| A14 | ME 菜单取物与 JEI 获取检查 | 包须对应当前菜单；资源序号有效且是物品、网络在线、数量正数；抽取受玩家背包容量、实际能源与 IActionSource 限制。JEI 获取沿用 JEI 自身作弊权限。 | 忽略失效请求／减少实际取出量 | `无独立开关；原生权限` |
| A15 | 规划节点／时间／循环搜索预算 | 每次编译默认 100,000 节点、2,000 ms；循环默认 SCC 256 节点、1,000,000 搜索状态、1,000 ms。超过预算结束优化尝试，可能回退原生。 | 优化回退／有界搜索 | `max_nodes；compile_budget_ms；cycle_solver.max_scc_nodes/max_search_states/budget_ms` |
| A16 | 计算并发和取消检查 | AELIS 后台并发为 clamp(处理器数/2,1,8)，另有 1 个交互保留位；没有空位会排队；线程被中断才取消。 | 排队／响应取消 | `固定；自动或精确 AELIS 接管时生效` |
| A17 | 物品身份复检与缓存限额 | 外部库存每次抽取复查物品 key；槽位改变类型则停止取原请求物品。样板缓存默认每样板 32 项，超量淘汰／回退。 | 停止错误物品抽取／缓存淘汰 | `performance.storage_bus.enable_slot_index=true；io_bus.enable_slot_routing=true；pattern_cache.enabled=true` |
| A18 | CPU 进度和 long 投影饱和 | CPU 任务／物品汇总及时间跟踪使用精确值或 Long.MAX_VALUE 饱和显示。CraftingAmountProjection 对负数参数仍抛异常。保留 LongSafety 名的 accessor／进度 Mixin 不等于拒绝计划。 | 替换进度统计／饱和；不拒绝正数超限计划 | `固定` |
| A19 | 已移除：优化失败后禁止原生回退 | 包括精确请求、大额数量和 overflow/native_boundary_work_limit 原因，均不再抛 AelisPlanningLimitException。 | 原生继续；最终数量记录实际 long 结果 | `拒绝类和提示已删除` |
| A20 | 循环运行存档无效时取消合成 | AE 原生或 AdvancedAE CPU 正在运行且带循环运行标签，读取标签却不能恢复有效 runtime 时调用 cancel；旧缺 phase 状态则尝试恢复，失败可保留旧 runtime。 | 主动取消损坏的循环任务 | `固定；与 C02 具体恢复分支相关` |
| A21 | 精确 CPU 状态网络包大小 | 精确状态读包每个数字 byte array 上限 65,536，总数字预算 262,144；索引／条数须属于当前 entries，数字不可负，批次输入最多 64 且必须为正。规划结果同步也有有界数字／映射读取。 | 解码拒绝；不限制服务端总订单数量 | `固定` |

## OmniSequence: Transfinite

| 编号 | 检查 | 触发条件与作用 | 结果 | 开关／默认值 |
| --- | --- | --- | --- | --- |
| O01 | 共用 KeyCounter 溢出饱和 | 拦截 AE2 KeyCounter.add/addAll；正溢出设 Long.MAX_VALUE，负溢出设 Long.MIN_VALUE，跳过原加法。影响共享 AE 库存计数，不只 Omni 机器。 | 取消原加法并饱和；不会拒绝下单 | `固定；两个版本都保留` |
| O02 | 原生智能倍增绕过 Omni | 已开启原生倍增／已有外部倍率任务跳过 Omni 二次包装、批量展开和多输入规范化；原生供应器优先。 | 跳过本地倍率路径；普通任务仍可批量 | `通过 Applied 的兼容识别；无独立开关` |
| O03 | 批量样板有效性与提供者能力 | 空输入、非法模板或提供者未声明支持，不启用本地批量；模板／提供者迭代出错则回退。 | 回退普通逐次派发；不是整个订单拒绝 | `omni_computation.dispatch.omni_batch_dispatch_enabled=true` |
| O04 | 一批材料和输出可表示范围 | 实际抽取按库存、AE 能量、提供者容量、输入／输出 long 空间共同缩小；单批乘法或累计溢出、输入不匹配会拒收／回退；已取附加材料回滚。 | 缩小批次／拒绝单批，保留调用方材料 | `固定 long 接口容量；没有总订单 long 白名单` |
| O05 | 可复用工具、容器和动态配方检查 | 必须验证真实选料与返还语义；随机损伤、未知状态转移、桶等不能未经证明批量扩展。确定性工具最多验证 2,048 次状态转移。 | 回退／缩小可复用批次 | `固定 2,048；显式适配器可支持` |
| O06 | 整批接收与所有权握手 | 接收方必须同步 accept/reject 一次；不回调按 INTERNAL_ERROR 拒收；重复决定、过期对象、异线程调用抛错；不能部分接收却宣称整批成功。 | 拒收／回滚未接收材料／异常 | `固定 API 合约` |
| O07 | 原生供应器队列和容量复检 | 批量已在进行、入料非法、目标接收数量不等于声明，不能当已验证整批成功；有输入队列没有方向的存档状态不能执行；已接受材料持续由队列保存。 | 拒绝／降低为未验证路径／等待队列返还 | `固定；沿用供应器阻挡设置` |
| O08 | 外部倍率拆解和 CPU 任务规范化 | 倍率 ABI 不可读、递归／非法包装、任务进度负数、规范化乘加溢出或不能写回任务，恢复原任务并阻止该异常规范化任务继续。 | 阻止异常任务路径／恢复快照；原生托管任务已绕过 | `固定` |
| O09 | 精确 CPU 任务／交付账本一致性 | 任务必须和计划定义一致；任务缺失、重复定义、无匹配、超量扣任务／信用、存档精确数不合法不能装入或提交记账。 | 异常／停该异常执行路径／保留恢复状态 | `固定` |
| O10 | 中间产物直接返还绑定检查 | 只有 schema 匹配、服务端主线程、同网且已经验证的无用之物发送者／CPU 回调，才能直接返还；未绑定和普通任务继续外部原生返还。 | 跳过直接返还／回退原生；不删外部队列 | `omni_direct_native_output_return=true` |
| O11 | 独立自动合成的产量和留料限制 | 每槽位独立开关、可设置产物上限和各输入保护量；达到上限、保护后材料不足、无能量或主机繁忙时不生产。 | 等待／停止该槽位本轮 | `界面配置；产物上限和保护量 0 表示无此限制` |
| O12 | 本地输出缓存容量 | 单块／阵列自动合成缓存最多 256 个资源类型，累计 long 放不下时拒收；物质构筑井实体槽／流体槽为 int 范围，样板缓冲按 long 剩余空间背压。 | 拒绝单批／等缓存排出 | `固定容量与实际槽位；非订单上限` |
| O13 | 研究前置、阶段和启停 | 前置未满足、满阶、结构／网络未就绪、同研究已有任务、旧不可读任务或待退款时不能新下单；准备阶段能停止，开始研究后不能停止准备。 | 拒绝新研究／等待材料／保留旧状态 | `配方 prerequisiteLevels/depths；无统一跳过开关` |
| O14 | 研究材料分配与订单保留 | 研究材料按全部成本分配；专属下单产物存入研究预留，其他合成不能抢走；不匹配材料不接收；预留 long 空间不足减少接收；退款未清先等待。 | 过滤收料／预留材料／等待 | `固定；此前用户要求保留的依赖材料修复` |
| O15 | 构筑井配方解锁和导入约束 | 需研究的配方未解锁不能执行；标准配方最多 12 个物品/AE 输入、6 个物品输出并检查 AE 输出数、另有流体槽；空成本／产物、非正数资源、不可读外部配方或超槽位导入会拒绝／跳过；标准配方时间至少 1 tick、AE 耗能至少 0（会归一化），研究配方另校验有效时间／能量。 | 配方不执行／导入跳过／数据错误 | `配方 requiresResearch；其他固定` |
| O16 | 多方块成型、材料和操作互斥 | 结构不完整、冲突、越界、未加载、缺材料、正在搭拆／更新／回收时，不能开始冲突操作或继续当前位置；存储满先保留回收物。 | 拒绝开始／暂停等待；不是删掉材料 | `实际结构状态；force_load_chunks=true 影响可加载性` |
| O17 | 建拆权限、距离和操作人 | 服务端校验同维度、可建造、mayInteract/mayUseItemAt；天枢管理距离 ≤8 格。部分暂停／取消要求原操作人或权限等级 2；执行人离线或跨维度等待。 | 拒绝无权限操作／暂停 | `无独立总开关；原生权限与操作状态` |
| O18 | 移动部件碰撞、归位和迁移 | 区块未加载、目标有障碍、权限不允许、操作人离线、已有便携部件／归位过程时不能启动／迁移；遇障碍冻结或等待，回滚已迁移状态。 | 拒绝启动／冻结／等待／回滚 | `固定；运动绑定采集启停` |
| O19 | AE CPU 多方块隔离 | Omni 控制器只形成自己的独立 1×1×1 AE CPU；原生 AE CPU 不能把 Omni 核心吸入，包围其他 Omni 核心的扫描也判无效。 | 取消／否定原生 AE CPU 成型扫描 | `固定；防止非 AE 单元类型强转崩溃` |
| O20 | 量子奇点类型和频率占用 | 量子槽只收量子缠绕态奇点，须有有效频率；同一服务器相同频率不能被两个本模组桥接宿主同时占用；冲突时不建立链接。 | 拒绝插入／不建立重复链接 | `固定；无独立开关` |
| O21 | 复制样品和黑洞槽过滤 | 黑洞槽只接受微型黑洞，最大 64；量子槽不收黑洞；样品槽保留样品，槽位／Shift 转移按对应语义检查。 | 拒绝错误槽位插入／容量限制 | `固定 64` |
| O22 | 采集标签白名单与物品黑名单 | 只生成已配置物品标签实际命中的物品，合并去重后用完整物品 ID 黑名单排除；空标签不生成，黑名单优先。 | 过滤产物 | `singularity_hub.singularity_collection.item_tags；item_blacklist=[]` |
| O23 | 采集启动／运行条件 | 结构成型、网络在线、无冲突操作且运动可启动才能开启；40 tick 启动期后生产，结构／网络／运动失效取消采集；存储容量不足只存可接收量。 | 拒绝启动／停采集／减产 | `batch_size=1000；interval_ticks=20；固定启动 40 tick` |
| O24 | 序质供能、复制耗材与回收背压 | 成型且在线、黑洞>0 才生成；按 fe/ae 优先级、实际能量和 ME 流体空间减产。复制 1 件需要 1,000 mB；未退完流体或未排完复制产物先等待。 | 不生产／减产／等回收；样品保留 | `energy_priority=[fe,ae]；fe_per_unit=1000；ae_per_unit=256；matter_per_black_hole=20；interval_ticks=20` |
| O25 | 微型白洞唯一性 | 当前维度已有登记且仍在的白洞，另一个放置返回失败。源码实际为每维度一个，不是跨所有维度全服一个；拆除释放登记。 | 拒绝再次放置 | `固定；两个版本同样` |
| O26 | 黑洞不吸玩家且需白洞 | 只吸半径 12 格内非玩家、未移除的实体；没有有效同维度白洞就不吸。到 1.35 格内转送白洞，玩家始终排除。 | 过滤实体／无出口时停止吸引 | `固定；此前用户明确要求` |
| O27 | 多方块占用区块自然刷怪拦截 | 在登记多方块占用区块的所有高度，NATURAL、CHUNK_GENERATION、PATROL、REINFORCEMENT 刷怪判失败／取消；已有实体及其他生成来源不按此规则删除。 | 取消这四类刷怪 | `固定；无独立刷怪开关` |
| O28 | EAEP 重复样板上传过滤 | 阵列已经有相同样板时阻止重复上传；编码菜单入口把新编码样板转回空白样板并返还无法放回的部分。没有可用阵列则继续 EAEP 原生路径。 | 拒绝重复上传／转回空白样板 | `需 EAEP 上传部件；无单独开关` |
| O29 | 派发工作／时间预算 | 控制器每 tick 共用工作预算默认 2,147,483,647；兼容调用上限默认 2,147,483,647，时间 50,000 µs；虚拟 CPU 公平共享，预算用完后续 tick 再派。 | 延后派发；不拒绝整个订单 | `omni_dispatch_max_work_units；omni_compat_dispatch_max_calls_per_tick；omni_compat_dispatch_max_time_us` |
| O30 | 直接返还及自适应批次性能限额 | 无用之物直接返还共享每 tick 2 ms；只在可验证绑定路径工作。自适应窗口根据返还时间／积压缩放，不能小于一个真实不可分批任务，窗口本身无固定数值上限。 | 延后返还／缩小批次；无数量硬性总上限 | `2 ms 固定；omni_direct_native_output_return 开关；外部原生容量仍有效` |
| O31 | 自动合成／研究／搭建节奏限额 | 独立合成每槽每次最多 64 次；构筑井／天枢搭拆每 tick 64 块，阵列按配置默认 32 块；研究材料轮询 20 tick、失败重试 100 tick；检修／界面刷新和拆除二次点击也有等待窗口。 | 分 tick／缓存／限频；建议按 TPS 要求保留 | `sequence_array.build_blocks_per_tick=32；其余固定节奏` |
| O32 | 网络同步大小与失效状态 | 样板索引分包 64 行、每侧最多 256 key、每包 4,096 key、来源槽位 ≤1,000,000；索引构建展示每侧最多 16 key。过期菜单／代次忽略；研究可视最多 4 任务，仅影响同步显示。 | 拒绝非法包／分包／裁剪展示 | `固定；不等同实际研究任务上限` |
| O33 | Forge 配方 JSON 同步限额 | 仅 1.20.1：最终配方 JSON 单包最多 32 条、总 250,000 字符；超大单配方跳过客户端同步，非法数量／过大包解码拒绝。服务端配方执行与客户端同步分开。 | 跳过同步／拒绝非法包 | `Forge 专有；1.21.1 使用原生配方同步` |
| O34 | 精确 Omni CPU 初始化／恢复失败取消任务 | Omni CPU 已接单，但精确任务账本无法初始化或存档无法重绑时，清精确状态、调用 cancel；提交返回 INCOMPLETE_PLAN。不是仅显示告警，也不是自动降成 long 继续执行。 | 主动取消异常精确合成任务 | `固定；大整数精确执行路径` |
| O35 | AE2Utility NBT Tear 无供应器回退 | 外部 NBT Tear 匹配抛 NoSuchElementException 时，返回精确 input.matches(template)；不再按其可撕 NBT 规则继续匹配；clear 将 ThreadLocal 设 null 而非 remove。 | 替换匹配／捕获异常；可能使某候选不匹配 | `只在 AE2Utility 存在时；无独立开关` |
| O36 | Omni 计算并发、取消与 CPU 路由 | 有成型 Omni 控制器时，自动 AELIS 关闭的计算共用后台 clamp(处理器数/2,1,8) 和 1 个交互位；没位排队、中断取消。自动找 CPU 可优先合适 Omni，手选忙 Omni CPU 可转到本控制器空闲 lane；结构失效的 lane 不 active。 | 排队／取消旧计算／改路由；不增加订单数量拒绝 | `并发固定；自动 AELIS 开时由 A16 管理` |

## 共用的内部合约与日志限制

| 编号 | 检查 | 触发条件与作用 | 结果 | 开关／默认值 |
| --- | --- | --- | --- | --- |
| C01 | 运行对象、线程和 API 状态验证 | 精确规划和研究修改必须在宿主服务端线程；空 key／负数量／非法槽号／缺计划、错误 scope 嵌套或过期批量上下文抛参数／状态异常。注册 ID 重复或空白也不能注册。 | 异常／忽略非法调用 | `固定；API 基础合约，不是新的订单容量拦截` |
| C02 | NBT 存档版本和所有权检查 | 不认识的版本、非法数、缺 key、任务数量与账本不匹配时不把损坏状态作为可运行任务；旧任务／材料进入各自恢复、隔离或保持旧运行状态。具体恢复分支依子系统。 | 拒绝加载运行状态／保留恢复数据 | `固定；不可统一改成忽略所有错误` |
| C03 | 诊断和重复日志限流 | 两个模组反复相同日志模板按全局 60 秒窗口限流；Applied 保存 128 个分类，超额共用窗口；性能汇总约 10 秒。首次故障保留，不改变材料和任务结果。 | 只减少日志输出；不拦截合成 | `diagnostics 默认 false；性能采样默认 false` |

## 两版本差异与范围

绝大部分上述规则在两个版本一致。A01 在 Forge 关闭增强模式时按实际原生菜单 ABI 判断：原版 AE 15 为 int 菜单，UELM 可为 long；Neo 使用 AE 19 原生 int 菜单。A17 的每次取物身份复检分别适配 AE 15 和 AE 19。O33 只存在 Forge，Neo 配方由其原生 codec／同步体系处理。原 A19 位于 Applied 自动／精确 Mixin 入口，现已移除；Omni 原有显式 API 回退流程继续保留。

Forge 微型黑洞每 5 tick 查询一次实体，随后每 tick 复查并施加吸力；Neo 当前每 tick 查询实体。两版都排除玩家、要求同维度白洞，这是扫描节奏差异，未在本次审计中擅自更改。运动碰撞在 Forge 另适配 Radium 查询，Neo 使用其 Level 碰撞入口；都属于增加可碰撞表面，不是阻止下单。

Neo 的 LDLib XML 布局缺失／缺所需元素会抛 UI 初始化错误；Forge 使用另一套布局。可选 AE2CT 精确树布局 ABI 不兼容也会抛错；这些属于界面集成失败，并非服务端材料／数量拒绝。

重复样板筛选按钮只改变终端显示，O28 才是重复上传的服务端操作过滤。`projectionSaturated`／旧 `previewOnly` 标记和精确能力查询用于告知投影状态，并不单独强制拒绝 CPU；A07 的循环 CPU 提交拒绝已移除，能力查询仅供实现参考。第三方模组自己的限制、AE 原生安全权限和磁盘行为不由此清单授权删除。

## 源码核对

下面为各组的代表入口；一组内的关联实现会在入口中调用。A20 另见 AdvancedAeCraftingCycleMixin；O36 路由另见 OmniCraftingServiceMixin/OmniCraftingCpuClusterMixin。C01/C02/C03 同时存在两个项目，列出 Applied 的入口并补充 Omni 入口。

| 编号 | 1.20.1 Forge | 1.21.1 NeoForge |
| --- | --- | --- |
| A01 | [CraftAmountMenuMixin.java:39](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/CraftAmountMenuMixin.java#L39) | [CraftAmountMenuMixin.java:38](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/CraftAmountMenuMixin.java#L38) |
| A02 | 已移除拒绝，见当前规则表 | 已移除拒绝，见当前规则表 |
| A03 | [AelisPlanner.java:50](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L50) | [AelisPlanner.java:50](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L50) |
| A04 | [AelisPlanner.java:51](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L51) | [AelisPlanner.java:51](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L51) |
| A05 | [AelisPlanner.java:461](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L461) | [AelisPlanner.java:461](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/github/appliedenhancements/crafting/aelis/AelisPlanner.java#L461) |
| A06 | [AelisCycleRuntimeController.java:136](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/api/AelisCycleRuntimeController.java#L136) | [AelisCycleRuntimeController.java:136](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/api/AelisCycleRuntimeController.java#L136) |
| A07 | 已移除拒绝，见当前规则表 | 已移除拒绝，见当前规则表 |
| A08 | [ManualCraftingInventoryLock.java:72](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/ManualCraftingInventoryLock.java#L72) | [ManualCraftingInventoryLock.java:72](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/ManualCraftingInventoryLock.java#L72) |
| A09 | [InfiniteCraftingSimulationMixin.java:60](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/InfiniteCraftingSimulationMixin.java#L60) | [InfiniteCraftingSimulationMixin.java:60](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/InfiniteCraftingSimulationMixin.java#L60) |
| A10 | [SmartDoublingPatternAccess.java:50](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/SmartDoublingPatternAccess.java#L50) | [SmartDoublingPatternAccess.java:50](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/SmartDoublingPatternAccess.java#L50) |
| A11 | [ExactScaledTaskReconciliation.java:61](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/ExactScaledTaskReconciliation.java#L61) | [ExactScaledTaskReconciliation.java:61](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/ExactScaledTaskReconciliation.java#L61) |
| A12 | [CraftConfirmMenuMixin.java:79](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/CraftConfirmMenuMixin.java#L79) | [CraftConfirmMenuMixin.java:77](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/CraftConfirmMenuMixin.java#L77) |
| A13 | [PatternAccessTermMenuBatchMoveMixin.java:62](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/PatternAccessTermMenuBatchMoveMixin.java#L62) | [PatternAccessTermMenuBatchMoveMixin.java:62](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/PatternAccessTermMenuBatchMoveMixin.java#L62) |
| A14 | [MEStorageMenuContextMixin.java:38](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/MEStorageMenuContextMixin.java#L38) | [MEStorageMenuContextMixin.java:38](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/MEStorageMenuContextMixin.java#L38) |
| A15 | [Config.java:107](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/Config.java#L107) | [Config.java:107](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/Config.java#L107) |
| A16 | [AelisCraftingCalculationMixin.java:45](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/AelisCraftingCalculationMixin.java#L45) | [AelisCraftingCalculationMixin.java:45](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/AelisCraftingCalculationMixin.java#L45) |
| A17 | [ExternalStorageFacadeItemHandlerMixin.java:48](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/ExternalStorageFacadeItemHandlerMixin.java#L48) | [ExternalStorageFacadeItemHandlerMixin.java:47](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/ExternalStorageFacadeItemHandlerMixin.java#L47) |
| A18 | [CraftingAmountProjection.java:7](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/CraftingAmountProjection.java#L7) | [CraftingAmountProjection.java:7](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/CraftingAmountProjection.java#L7) |
| O01 | [KeyCounterMixin.java:23](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/KeyCounterMixin.java#L23) | [KeyCounterMixin.java:23](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/KeyCounterMixin.java#L23) |
| O02 | [MolecularBatchDispatchSafety.java:32](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchDispatchSafety.java#L32) | [MolecularBatchDispatchSafety.java:32](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchDispatchSafety.java#L32) |
| O03 | [MolecularBatchDispatchSafety.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchDispatchSafety.java#L37) | [MolecularBatchDispatchSafety.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchDispatchSafety.java#L37) |
| O04 | [MolecularBatchCraftingExtractor.java:1204](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchCraftingExtractor.java#L1204) | [MolecularBatchCraftingExtractor.java:1204](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularBatchCraftingExtractor.java#L1204) |
| O05 | [MolecularReusableInputAdapters.java:20](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularReusableInputAdapters.java#L20) | [MolecularReusableInputAdapters.java:21](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularReusableInputAdapters.java#L21) |
| O06 | [MolecularOmniBatchDelivery.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularOmniBatchDelivery.java#L37) | [MolecularOmniBatchDelivery.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularOmniBatchDelivery.java#L37) |
| O07 | [PatternProviderLogicMixin.java:102](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/PatternProviderLogicMixin.java#L102) | [PatternProviderLogicMixin.java:103](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/PatternProviderLogicMixin.java#L103) |
| O08 | [MolecularExternalScaledPattern.java:311](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularExternalScaledPattern.java#L311) | [MolecularExternalScaledPattern.java:323](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MolecularExternalScaledPattern.java#L323) |
| O09 | [OmniExactCraftingState.java:93](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/OmniExactCraftingState.java#L93) | [OmniExactCraftingState.java:94](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/OmniExactCraftingState.java#L94) |
| O10 | [UselessExactOutputReturn.java:75](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/integration/useless/UselessExactOutputReturn.java#L75) | [UselessExactOutputReturn.java:75](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/integration/useless/UselessExactOutputReturn.java#L75) |
| O11 | [MolecularAutoCrafter.java:130](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafter.java#L130) | [MolecularAutoCrafter.java:138](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafter.java#L138) |
| O12 | [MolecularAutoCrafterBlockEntity.java:50](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafterBlockEntity.java#L50) | [MolecularAutoCrafterBlockEntity.java:50](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafterBlockEntity.java#L50) |
| O13 | [MatterResearchProgress.java:69](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/research/MatterResearchProgress.java#L69) | [MatterResearchProgress.java:69](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/research/MatterResearchProgress.java#L69) |
| O14 | [MatterResearchProgress.java:136](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/research/MatterResearchProgress.java#L136) | [MatterResearchProgress.java:136](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/research/MatterResearchProgress.java#L136) |
| O15 | [MatterFabricationRecipe.java:31](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/crafting/MatterFabricationRecipe.java#L31) | [MatterFabricationRecipe.java:32](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/crafting/MatterFabricationRecipe.java#L32) |
| O16 | [SingularityBlockEntity.java:359](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L359) | [SingularityBlockEntity.java:360](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L360) |
| O17 | [SingularityBlockEntity.java:332](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L332) | [SingularityBlockEntity.java:333](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L333) |
| O18 | [SingularityMotionState.java:43](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityMotionState.java#L43) | [SingularityMotionState.java:43](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityMotionState.java#L43) |
| O19 | [CraftingCPUCalculatorMixin.java:33](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/CraftingCPUCalculatorMixin.java#L33) | [CraftingCPUCalculatorMixin.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/CraftingCPUCalculatorMixin.java#L37) |
| O20 | [EntangledQuantumFrequencyRegistry.java:21](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/integration/ae2/EntangledQuantumFrequencyRegistry.java#L21) | [EntangledQuantumFrequencyRegistry.java:21](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/integration/ae2/EntangledQuantumFrequencyRegistry.java#L21) |
| O21 | [SingularityMenu.java:30](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/menu/SingularityMenu.java#L30) | [SingularityMenu.java:30](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/menu/SingularityMenu.java#L30) |
| O22 | [ModConfig.java:70](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/config/ModConfig.java#L70) | [ModConfig.java:71](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/config/ModConfig.java#L71) |
| O23 | [SingularityBlockEntity.java:426](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L426) | [SingularityBlockEntity.java:427](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityBlockEntity.java#L427) |
| O24 | [SingularityDuplicationProcessor.java:35](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityDuplicationProcessor.java#L35) | [SingularityDuplicationProcessor.java:35](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/SingularityDuplicationProcessor.java#L35) |
| O25 | [WhiteHoleRegistry.java:22](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/world/WhiteHoleRegistry.java#L22) | [WhiteHoleRegistry.java:24](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/world/WhiteHoleRegistry.java#L24) |
| O26 | [CosmicSingularityBlockEntity.java:59](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/CosmicSingularityBlockEntity.java#L59) | [CosmicSingularityBlockEntity.java:46](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/CosmicSingularityBlockEntity.java#L46) |
| O27 | [MultiblockSpawnProtection.java:19](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/world/MultiblockSpawnProtection.java#L19) | [MultiblockSpawnProtection.java:19](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/world/MultiblockSpawnProtection.java#L19) |
| O28 | [ExtendedAEPlusPatternUploadMixin.java:38](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/ExtendedAEPlusPatternUploadMixin.java#L38) | [ExtendedAEPlusPatternUploadMixin.java:38](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/ExtendedAEPlusPatternUploadMixin.java#L38) |
| O29 | [OmniComputationCoreBlockEntity.java:878](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/OmniComputationCoreBlockEntity.java#L878) | [OmniComputationCoreBlockEntity.java:873](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/OmniComputationCoreBlockEntity.java#L873) |
| O30 | [UselessExactOutputReturn.java:19](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/integration/useless/UselessExactOutputReturn.java#L19) | [UselessExactOutputReturn.java:19](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/integration/useless/UselessExactOutputReturn.java#L19) |
| O31 | [MolecularAutoCrafter.java:50](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafter.java#L50) | [MolecularAutoCrafter.java:57](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/blockentity/MolecularAutoCrafter.java#L57) |
| O32 | [PatternSearchIndexPayload.java:22](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/network/PatternSearchIndexPayload.java#L22) | [PatternSearchIndexPayload.java:26](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/network/PatternSearchIndexPayload.java#L26) |
| O33 | [MachineRecipeJsonPayload.java:27](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/network/MachineRecipeJsonPayload.java#L27) | — |
| C01 | [AelisExactCraftingService.java:32](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/api/AelisExactCraftingService.java#L32) | [AelisExactCraftingService.java:32](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/api/AelisExactCraftingService.java#L32) |
| C02 | [AelisCycleExecutionNbt.java:16](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/AelisCycleExecutionNbt.java#L16) | [AelisCycleExecutionNbt.java:17](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/AelisCycleExecutionNbt.java#L17) |
| C03 | [AelisPlanningLog.java:13](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/runtime/AelisPlanningLog.java#L13) | [AelisPlanningLog.java:13](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/runtime/AelisPlanningLog.java#L13) |
| A19 | 已移除拒绝，见当前规则表 | 已移除拒绝，见当前规则表 |
| A20 | [CraftingCpuLogicBatchMixin.java:379](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/CraftingCpuLogicBatchMixin.java#L379) | [CraftingCpuLogicBatchMixin.java:384](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/CraftingCpuLogicBatchMixin.java#L384) |
| A21 | [CraftingStatusExactMixin.java:74](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/src/main/java/com/appliedenhancements/mixin/CraftingStatusExactMixin.java#L74) | [CraftingStatusExactMixin.java:74](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/src/main/java/com/appliedenhancements/mixin/CraftingStatusExactMixin.java#L74) |
| O34 | [CraftingCpuLogicMixin.java:372](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/CraftingCpuLogicMixin.java#L372) | [CraftingCpuLogicMixin.java:372](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/CraftingCpuLogicMixin.java#L372) |
| O35 | [NbtTearPatternMatchHelperMixin.java:23](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/NbtTearPatternMatchHelperMixin.java#L23) | [NbtTearPatternMatchHelperMixin.java:23](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/NbtTearPatternMatchHelperMixin.java#L23) |
| O36 | [OmniCraftingCalculationMixin.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.20.1-forge/src/main/java/com/atir/molecularmanipulator/mixin/OmniCraftingCalculationMixin.java#L37) | [OmniCraftingCalculationMixin.java:37](https://github.com/AyaYumi/OmniSequence-Transfinite/blob/1.21.1-neoforge/src/main/java/com/atir/molecularmanipulator/mixin/OmniCraftingCalculationMixin.java#L37) |

Omni 内部状态／持久化另见 `MolecularOmniBatchDelivery`、`OmniExactCraftingState`、`MatterPatternBuffer`、`MolecularReusableBatchJob`、`MatterResearchProgress`，日志见 `diagnostics/RateLimitedLog`。Applied 的 A04 深度见 `AelisRecursionGuard.MAX_TRANSACTIONAL_DEPTH=256`；A11 的外部 ABI 读取见 `SmartDoublingPatternAccess`；A18 进度见 `ExecutingCraftingJobLongSafetyMixin` 和 `CraftingCpuLogicBatchMixin`。

## 此前原生算术拒绝删除验证

两模组配套加载的变换后游戏测试在 AE2 15.4.10、UELM 15.5.4、AE2 19.2.18 和 AE2 19.2.17 各通过 4 项。新用例验证圆石／水 `Long.MAX_VALUE + 80000` 不再被 Applied 拒绝、有限库存不会无限供给、已标记无限回流与跨 long 消耗精确记账，以及 native addCrafting 精确累计。原有用例验证错误物品复检、原生智能倍增混合任务、30 亿次批量与 120 亿输出持久化。

19.2.17 最初复用旧夹具世界时长期批量输出断言失败；新建隔离世界后全部通过。测试启动器现允许指定新的 build 下目录。此处是受影响路径的回归，不把之前整合包的 37 项完整流程和 MSPT 记录当成本次重新实测结果。1.21.1 UELM 未在此次环境中取得对应运行件，本次只明确报告 AE 19.2.17／19.2.18 的实测。

## 本次指定删除验证（2026-10-04）

A02/A03/A07/A11/A19 拒绝已从 Forge 与 NeoForge 删除。配套加载的真实变换后游戏测试在 AE2 15.4.10、UELM 15.5.4-uelm、AE2 19.2.18 和 AE2 19.2.17 各通过 7 项。新增用例验证百万以上显式精确请求在大整数偏好关闭时原生回退成功、指定 CPU 提交交给 AE 本身处理、倍率不一致修复尾数、未知任务恢复原始工作量，以及请求 100 个而库存只够 23 个时 CRAFT_LESS 的最终数量为 23。Applied 单元测试 NeoForge 412 项／Forge 421 项通过。其余编号仍保留。原生 AE 回退及未适配 CPU 本身仍使用 long，接受操作不代表任意精度执行；本次没有持续整合包 TPS 实测。
