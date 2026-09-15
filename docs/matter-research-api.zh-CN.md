# 物质构筑井：配方与研究 API

自 OmniSequence: Transfinite **2.0.0** 起提供，当前对应 **2.0.4**。
目标环境：Minecraft **1.21.1** / NeoForge、Java **21**、AE2 **19.2.17+**，以及必需前置
AppliedEnhancements **1.0.6+**。模组 ID 仍为 `molecularmanipulator`。

其他语言：[English](matter-research-api.md)。
另见[接口索引](README.md)与独立的[批量供应器 API v1](omni-batch-provider-api.md)。

本文档覆盖构筑井的两类配方：

| 配方类型 | 作用 |
| --- | --- |
| `molecularmanipulator:matter_fabrication` | 物质构筑井能加工什么。 |
| `molecularmanipulator:matter_research` | 解锁这些配方并提供生产加成的研究。 |

两者都是 `data/<namespace>/recipe/` 下的普通数据包配方。KubeJS 使用
`ServerEvents.recipes` 与 `event.custom` 添加或替换，不需要额外适配插件。研究进度属于
每个构筑井控制器，不属于玩家，也不属于全局网络。

---

## 1. 快速上手：用研究锁住自定义构筑井配方

```javascript
ServerEvents.recipes(event => {
  // 1) 解锁自定义配方的研究。
  event.custom({
    type: 'molecularmanipulator:matter_research',
    title: '石材深度研究',
    stage: 3,
    sort_order: 100,
    prerequisites: ['molecularmanipulator:research/ae_foundation'],
    ingredients: [{ingredient: {item: 'ae2:fluix_crystal'}, count: '32'}],
    duration: 1200,
    ae_per_tick: 128,
    unlocks: ['kubejs:stone_processing'],
    depths: [
      {parallel: '4'},
      {material_multiplier: '3', parallel: '1024', speed_multiplier: '8'},
      {
        ingredients: [
          {ingredient: {item: 'ae2:fluix_crystal'}, count: '512'},
          {ingredient: {item: 'minecraft:diamond'}, count: '64'}
        ],
        parallel: '9223372036854775807',
        processing_ticks: 1
      }
    ]
  }).id('kubejs:stone_research');

  // 2) 构筑井配方本身。
  event.custom({
    type: 'molecularmanipulator:matter_fabrication',
    ingredients: [{ingredient: {item: 'minecraft:cobblestone'}, count: 1}],
    results: [{id: 'minecraft:stone', count: 1}],
    processing_time: 200,
    ae_per_tick: 64,
    requires_research: true
  }).id('kubejs:stone_processing');
});
```

`requires_research: true` 表示该配方锁定，直到 `unlocks` 中列出它的**任意一个**研究完成
至少 1 次——此处即 `kubejs:stone_research`。

### 1.1 把配方锁在更高的研究等级之后

配方权限是二元的：完成 1 次即开放，配方本身没有等级字段。若要要求特定深度——例如
**万物演算 9/9**——应新增一个“门槛研究”，把等级要求写在它的前置上，再由它解锁配方，
而不是由内置研究直接解锁：

```json
{
  "type": "molecularmanipulator:matter_research",
  "title": "kubejs.research.late_stone",
  "prerequisites": ["molecularmanipulator:research/omni_computation"],
  "prerequisite_levels": {
    "molecularmanipulator:research/omni_computation": 9
  },
  "ingredients": [{"ingredient": {"item": "ae2:fluix_crystal"}, "count": "64"}],
  "duration": 2400,
  "ae_per_tick": 512,
  "stage": 3,
  "sort_order": 200,
  "unlocks": ["kubejs:stone_processing"]
}
```

这样 `kubejs:stone_processing` 必须先完成一次门槛研究才能加工，而门槛研究本身要等
万物演算完成 9 次后才能开始。把 `9` 换成字符串 `"max"`，即要求该前置当前的最高次数。

注意：

- 这里的“次”指累计完成次数，不是研究上仅用于显示的 `stage` 阶段编号。
- 数值门槛不会被自动调低。若超过前置研究的 `depths` 长度，门槛研究将永远无法开始，
  配置时应确保在范围内。
- `prerequisite_levels` 的键会自动并入 `prerequisites`，因此只写映射也能建立依赖；
  映射中未列出的已有前置默认要求 1 次。

---

## 2. 研究定义：`molecularmanipulator:matter_research`

位置：`data/<namespace>/recipe/<path>.json`。

| 字段 | 含义 |
| --- | --- |
| `title` | 名称或翻译键。必填，不能为空字符串。 |
| `stage` | 显示阶数，默认 `1`。先后与门槛由前置列表决定，不由该数字决定。 |
| `sort_order` | 导航顺序，默认 `0`。定义按 `sort_order` 排序，其次按 ID 排序。 |
| `prerequisites` | 前置研究 ID 列表，每项默认要求完成 1 次；自引用与循环无效。 |
| `prerequisite_levels` | 可选映射 `{研究 ID: 所需完成次数或 "max"}`，逐项覆盖门槛；其中的 ID 会自动加入前置列表。 |
| `ingredients` | 基础轮次耗材，必填。每项为 `{ingredient: {item: ...} 或 {tag: ...}, count: 正 long}`。仅支持物品类原料——研究不能收取流体或通用 AE 资源。 |
| `duration` | 每轮耗时（tick），至少 `1`，默认 `1200`。 |
| `ae_per_tick` | 研究期间功耗，有限非负，默认 `256`。 |
| `required_mods` | 必须全部加载后该定义才会显示并运行。 |
| `unlocks` | 本研究发现并加成的完整**配方 ID**（不是物品 ID）。 |
| `depths` | 每轮费用与完成后总加成。数组长度即研究总次数；省略时使用 §3 的默认九次表。 |

### 2.1 `depths` 每一项

| 字段 | 含义 |
| --- | --- |
| `material_multiplier` | 本轮费用 = 基础 `ingredients` × 该值，默认 `1`。 |
| `ingredients` | 可选。提供时**完整替代**本轮材料并忽略倍率；`[]` 表示免费一轮。 |
| `parallel` | 完成后并行上限。必填正 long。 |
| `speed_multiplier` | 原配方时间除该值并向上取整，默认 `1`。 |
| `processing_ticks` | 大于 `0` 时固定加工 tick 数并优先于速度倍率；`0` 或省略则使用速度倍率。 |

### 2.2 long 数值与 `2^53 - 1`

所有 long 字段（`count`、`material_multiplier`、`parallel`、`speed_multiplier`）都可写十进制
字符串，推荐始终加引号。超过 JavaScript 精确整数上限 `2^53 - 1` 时**必须**使用字符串：
写 `'9223372036854775807'`，不能用浮点数或 `'9.22E18'`。系统不会替你检测精度损失，
写错的 JavaScript 数字只会静默变成错误的值。费用乘法超出 long 上限时拒绝开始，不会变成
负数或免费研究。

---

## 3. 默认九次进度

省略 `depths` 时使用，三个内置研究也使用该表。每轮费用独立收取，不是从上一轮补差额。
`parallel` 表示一次加工可执行多少份完整配方。

| 累计完成次数 | 本轮材料 / 基础材料 | 完成后的并行上限 | 完成后的加工时间 |
| --- | --- | --- | --- |
| 1 | ×1 | 1 | 原始耗时 |
| 2 | ×2 | 256 | 原始耗时 ÷2，向上取整 |
| 3 | ×4 | 65,536 | 原始耗时 ÷4，向上取整 |
| 4 | ×8 | 16,777,216 | 原始耗时 ÷8，向上取整 |
| 5 | ×16 | 4,294,967,296 | 原始耗时 ÷16，向上取整 |
| 6 | ×32 | 1,099,511,627,776 | 原始耗时 ÷32，向上取整 |
| 7 | ×64 | 281,474,976,710,656 | 原始耗时 ÷64，向上取整 |
| 8 | ×128 | 72,057,594,037,927,936 | 原始耗时 ÷128，向上取整 |
| 9 | ×256 | 9,223,372,036,854,775,807（约 9.22E18） | 固定 1 tick |

加工时间至少 1 tick。并行与速度加成只作用于该研究 `unlocks` 列出的构筑井配方，不改变研究
自身耗时。不同研究若解锁同一配方，取**最高**并行与**最短**耗时——加成以取最大值/最小值
合并，不相乘。

---

## 4. 构筑井配方：`molecularmanipulator:matter_fabrication`

| 字段 | 含义 |
| --- | --- |
| `ingredients` | 计数物品输入，每项 `{ingredient: {item: ...} 或 {tag: ...}, count: 1-64}`，默认空。 |
| `results` | 最多 **2** 种物品产物，严格格式 `{id: ..., count: ...}`。 |
| `fluid_input` | 可选流体输入 `{id: ..., amount: ...}`，单位 mB。不计入九项输入上限。 |
| `fluid_result` | 可选单种流体产物，格式相同。 |
| `ae_inputs` | 最多 **9** 项通用 AEKey 输入，见 §4.1。 |
| `processing_time` | 基础 tick，默认 `200`；小于 `1` 会被提升为 `1`。 |
| `ae_per_tick` | 基础功耗，默认 `64.0`；负值会被提升为 `0`。 |
| `requires_research` | 默认 `false`，见 §4.2。 |

加载期错误条件：`ingredients` + `ae_inputs` 合计不得超过 **9** 项；`ingredients`、
`fluid_input`、`ae_inputs` 至少要有一项；每项 `ae_inputs` 数量必须为正；且至少要有一种物品
或流体产物。与研究定义不同，格式错误的构筑井配方会直接加载失败，而不是留到加工时才拒绝。

### 4.1 通用输入（`ae_inputs`）

`ae_inputs` 接受所有已注册的 AEKey 类型，气体、化学品等第三方资源由此输入。每项使用 AE2
的 `GenericStack.CODEC`：`#t` 是已注册的 AEKey 类型 ID，`#` 是每份配方的原始数量。

| 字段 | 含义 |
| --- | --- |
| `#t` | AEKey 类型 ID。AE2 物品为 `ae2:i`，AE2 流体为 `ae2:f`；附属模组注册自己的 ID。 |
| `#` | 每份配方的原始数量，1 至 9223372036854775807。物品类型单位为个，NeoForge 流体单位为 mB。 |
| `id`、`components` 等 | 由所选类型解码；应使用该附属模组的 Codec，不要假设与物品/流体格式相同。 |

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ingredients": [{"ingredient": {"item": "minecraft:diamond"}, "count": 2}],
  "ae_inputs": [
    {"#t": "ae2:i", "id": "minecraft:diamond", "#": 2},
    {"#t": "ae2:f", "id": "minecraft:water", "#": 1000}
  ],
  "fluid_input": {"id": "minecraft:water", "amount": 250},
  "results": [{"id": "minecraft:obsidian", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64
}
```

`#` 使用 AE2 的数值 `Codec.LONG`，**不是**研究字段使用的十进制字符串格式，因此
`"3000000000"` 这种写法会被拒绝。需要超过 JavaScript 精确整数范围时，请在数据包 JSON 中写
整数字面量，或在 Java 中用 `new GenericStack(key, longAmount)` 构造，不要先经 JavaScript 数字
转换。

通用资源按完整 AEKey 精确匹配（含组件），而普通 `ingredients` 保持 Ingredient/tag 匹配。
重复或重叠的需求会累加——同一份库存不会重复抵扣两个不同需求。

含 `ae_inputs` 的配方必须由**样板总成**投料。手动物品与流体接口无法提供通用 AE 缓存，
会直接拒绝这类配方。

含通用输入的 Java 构造器：

```java
new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult,
        aeInputs, processingTime, aePerTick, requiresResearch); // aeInputs 为 List<GenericStack>
```

旧的 6 参数与 7 参数构造器保持可用，`aeInputs` 默认为空列表。产物仍由 `results` 与
`fluid_result` 定义，没有通用输出字段。

### 4.2 研究权限

当某项研究在 `unlocks` 中列出该配方，或配方 `requires_research` 为 `true` 时，配方处于锁定
状态。只要任一归属研究完成至少 1 次即可使用；多个研究归属时任意一个完成即可。
`requires_research: true` 在无人授予权限、授予研究被移除或缺少依赖模组时保持锁定。授予研究
当前不可用（例如缺少 AdvancedAE）时，配方同样锁定，而不是静默开放。

配方权限作用于物质构筑井，不全局拦截其他机器；已经建成的高级机器仍能使用。

---

## 5. 材料、扣料与保存

- 界面每 5 个服务器 tick 同步所选研究的当前 AE 实际库存。开始研究时再次直接读取存储提供者，
  重新校验并一次性提取整轮费用。研究仅消耗控制器所在 AE 网络中的**物品**，玩家背包和接口
  缓存都不是研究材料。
- 所有材料必须齐全才能开始。重叠的物品/标签要求通过一次联合分配求解，同一份库存不会重复
  计入两个需求，也不需要手工调整材料声明顺序。
- 若提供者在核验与实际提取之间发生变化，研究不启动，已提取材料退回；网络暂不接收的退款由
  控制器保存并重试，退款未完成前不接受新研究。
- 费用在开始时一次性扣完。暂停、断网、缺电、结构损坏或搭建/拆除期间都保留材料与进度，
  恢复研究不重复收费。
- 所有前置研究都必须达到各自要求的完成次数（默认 1 次）。若正在进行的后续研究因指令或配置
  变更不再满足前置要求，会保留材料与进度并等待。
- 同一研究同时至多运行一轮；不同分支独立计时、独立耗能，可与普通生产同时共享网络供电。
- 已开始任务保存材料、轮数、深度表、耗时与功耗快照；重载定义不会重新收费。完成次数按研究
  ID 保存，加工加成与解锁列表从当前定义计算。缩短深度表时保留原完成次数，并按新的最后一项
  重新计算加成。
- 旧存档的“已完成”迁移为完成 1 次；旧部分取材任务暂停，点击继续时一次性补齐尚未支付的
  部分，保留原进度与轮次。
- 控制器存档、正常掉落和 AE 拆卸物品都会保存完成次数、在研任务与待退款。记忆卡不复制这些
  内容。无法解码的任务或退款会原样保留，兼容环境重载后可恢复。

---

## 6. 内置研究与解锁链

| 研究 ID（前缀 `molecularmanipulator:research/`） | 每次研究时间 | 研究功耗 | 首次解锁内容 |
| --- | --- | --- | --- |
| `ae_foundation` | 600 tick / 30 秒 | 256 AE/t | 7 条 AE 材料配方、27 条二阶材料与中间材料配方、样板总成 |
| `sequence_array` | 600 tick / 30 秒 | 512 AE/t | 构序阵列 6 类部件、分子构序重写阵列、装配矩阵构序重写核心 |
| `omni_computation` | 600 tick / 30 秒 | 1024 AE/t | 万物演算 10 类部件与超限算枢 |

30 秒适用于内置研究的首次解锁及后续每轮深度研究。自定义 `duration` 仍按 tick 自由配置，
省略时默认 1200；已开始的轮次保留开工时的耗时快照。

两条二阶分支默认都需要一阶完成 **1 次**。一阶新增的 27 条配方 ID 形如
`molecularmanipulator:fabrication/research_materials/<模组 ID>/<物品名>`，覆盖两条二阶研究
消耗的 17 种材料，并补齐相关中间材料与量子注入液配方。它们在一阶首次完成后开放，并获得
一阶的深度研究加成。

无 AdvancedAE 时，万物演算分支及其材料配方不加载，界面也不列出它们。构序阵列分支由
`required_mods: ["extendedae"]` 约束。

分子构序重写阵列（`molecular_manipulator`）与装配矩阵构序重写核心
（`assembler_matrix_molecular_core`）已移入二阶构序阵列分支：工作台配方改为构筑井加工，
原材料种类与数量保持不变，基础加工均为 400 tick、512 AE/t。该分支首次完成后解锁这两条
配方，后续深度研究为它们提供同分支的速度与并行加成。已建成的高级机器仍能使用。

超限算枢配方 `molecularmanipulator:transfinite_compute_nexus` 由万物演算分支解锁，基础
耗时 1200 tick、功耗 4096 AE/t（未计生产加成），并继承该分支的构筑井加成，配方本身也要求
AdvancedAE 已加载。放置后单方块算枢的待机功耗单独配置，默认 16384 AE/t
（`transfinite_compute_nexus.idle_power`）；该配置只作用于单方块算枢，万物演算核心多方块
仍使用固定的 8192 AE/t 待机功耗。

构筑井控制器、5 类结构件和 4 类物品/流体接口使用 AE 原版材料制作，不受研究门槛影响；
样板总成需要在构筑井内加工，并要求一阶首次完成。

服务方块可在**正前方 24 格**与**中央四段平台各五格（共 20 格）**处与控制器连接，合计
44 个安装位；原外围九个位置已不再接受。样板总成与四种物品/流体接口使用同一组合法安装位。
这些方块本身可以放在任意位置，但只有落在合法安装位时才会与控制器连接；放置预览会高亮
合法安装位。

---

## 7. 批量加工、配方匹配与已知限制

手动接口加工与样板总成使用同一研究加成。手动物品缓存按实际库存、输出空间和供电计算本轮
份数；不会把 long 数量塞进普通 ItemStack。

样板总成实现公开的 `OmniBatchCraftingProvider` API，支持本模组演算系统的 long 批次。输入
支持所有已注册的 AEKey 类型，额外资源通过 `ae_inputs` 声明。输入与输出以 AE Key + long
持久保存，处理工作量取决于材料种类数，不随份数逐份循环。AE 普通单份投料也使用同一持久
批次，可在开始加工前合并同配方投料。

样板总成自行持有输入、输出与退款缓存。产物与待退回原料自动写回其 ME 网络；网络不接收时
保留在总成内，不转存到手动输出口。输出堵塞或保存重载不会丢弃已接收材料，单独拆除一块
也不会收走其他总成的库存。

配方查询按**完整产物及数量**建索引，并复用研究定义与配方归属索引。同产物候选仍按
RecipeManager 的顺序匹配，输入逐次完整校验。索引随配方管理器的配方快照替换自动重建，
覆盖数据包重载与 `replaceRecipes`。各控制器的完成次数实时读取，解锁、撤销和存档恢复不会
沿用其他控制器或旧状态的权限。

并行是上限而非保证：实际批次仍受材料、供电、输出接收能力和每种 AEKey 的 long 数量上限
限制。例如每份输出 16 个相同物品时，单批份数最多 `Long.MAX_VALUE / 16`。功耗按原配方每
tick 功耗乘本批份数计收。

原生测试通过公开 API 验证了数值侧能力：样板总成可接收并提交 30 亿份通用资源批次，且这些
数量在 NBT 往返后仍保持 long 精度；分子构序重写阵列与装配矩阵供应器可准备
`Long.MAX_VALUE / 4` 份批次并得到精确产物数量，乘法或缓存溢出会被拒绝且不消耗输入。这些
测试验证的是容量，不代表任何整合包有免费材料或供电。

已开始的任务保存开工时的加工快照；新任务或尚未开始的任务使用当前配方权限与加成。排队
批次保存原料所有权与配方 ID，因此未开工批次会按当前配方、权限与参数重新检查，而不是沿用
旧数值。无法继续加工时，原料仍留在总成内，可作为待加工原料退回。

### 7.1 2.0.4 尚存的边界

- 产物相同、可替代原料范围重叠时，拆分出的原料可能重新匹配另一条配方并改用其耗时和能耗。
- 重载时新增更靠前的匹配配方，可能导致已有队列等待，即使原配方仍存在。
- 无样板队列只在样板定义消失时退款，匹配到的配方变化不会触发退款。

产物索引与 API 准入都不应被视为这两类问题的修复。

---

## 8. JEI 与接口

JEI 配方顶部标注负责开放此配方的研究阶段与名称；鼠标悬停可查看完整研究条件，多个研究授予
同一配方时会全部列出。没有研究限制的配方显示“基础配方 · 无需研究”，研究条件未配置的配方
显示“研究条件未配置”。底部的时间和功耗为基础参数；深度研究加成由控制器实际应用。

| 接口 | 控制方式 |
| --- | --- |
| 物品输入口、流体输入口 | 已绑定控制器且 AE 在线时，点击“全部退回 AE”将本接口缓存退回该网络；接收不下的保留 |
| 物品输出口、流体输出口 | “自动输出”默认关闭；开启后每 5 tick 向已选方向的相邻容器转移，按实际接收量扣除缓存 |
| 输出方向 | 上、下、北、南、西、东独立切换，新接口六方向默认全部关闭。已开启方向使用 AE2 的高亮（浅蓝）按钮贴图并带方括号，鼠标悬停时按钮转为薄荷色，可多选；按钮显示该方向相邻方块的图标，悬停查看名称，方块改变后自动更新；使用世界方向，连接相邻容器朝向本接口的面 |
| 样板总成 | 显示 AE 连接状态和样板占用数；支持命名，窗口缩放保留尚未保存的名称 |

缓存容量为 16 个物品槽（4×4）与 4 个各 2,147,483,647 mB 的独立流体储罐；灌注时会优先合并到
已有的同类储罐。

流体输入口、流体输出口的四个缓存槽都支持手动双向存取：鼠标拿着流体容器右键指定槽，有流体
就尝试倒入，空容器就尝试装出；每次处理一个容器。单个水桶会变为空桶，单个空桶会变为水桶，
创造模式也进行实际容器交换。堆叠空桶取水时，水桶放进背包，鼠标上空桶减一；若背包放不下
结果，操作不生效。槽满、流体不兼容或不足以装满水桶时不会吞掉容器或流体。左键保留输入口
倒入、输出口取出的原操作。

输出缓存的自动转移无需保持界面打开。未选任何方向时停止输出；相邻区块未加载时不强制加载。
物品和流体输出仅发送到相邻容器，不直接丢到世界中。已保存的方向设置不会因更新默认值而重置。
输出开关、方向随世界保存，并保留在 AE 拆卸设置和控制器分批拆解打包的接口物品中。普通破坏
接口时，只有缓存中仍存有材料才会保存这些设置；空缓存被普通破坏只掉落普通方块，自动输出与
方向设置会丢失。

---

## 9. 管理指令

需要开启作弊或具有 OP 2 级权限。省略坐标时，准心需指向 16 格以内的物质构筑井控制器；也可
在指令末尾加 `x y z`，支持 `~` 相对坐标。服务器控制台必须指定坐标，目标区块必须已经加载。
指令仅操作目标控制器，不要求结构成型或 AE 在线，也不扣材料。

| 指令 | 效果 |
| --- | --- |
| `/matter_research unlock_all` | 将目标控制器的所有可用研究直接设为各自满级，包括 KubeJS 新增研究 |
| `/matter_research complete <研究 ID> true` | 将单项研究设为满级 |
| `/matter_research complete <研究 ID> false` | 将单项研究设为未完成，即 0 次 |
| `/matter_research set <研究 ID> <次数>` | 设置单项完成次数；0 表示未完成，超过上限自动设为最高次数 |

```mcfunction
/matter_research unlock_all
/matter_research set molecularmanipulator:research/ae_foundation 5
/matter_research complete molecularmanipulator:research/sequence_array true
/matter_research complete molecularmanipulator:research/sequence_array false
/matter_research set molecularmanipulator:research/omni_computation 999 100 64 200
```

研究 ID 支持 Tab 补全。计数参数接受 0 至 9223372036854775807，实际保存值会按该研究当前
`depths` 长度钳制。未加载 AdvancedAE 时，万物演算不会出现在补全和全部解锁列表中，单独
指定也会提示不可用。

设置某项完成状态或次数会结束该项正在进行的研究，已投入的材料不返还，也不会额外扣料；
`unlock_all` 会结束所有被设为满级的在研任务。单项设置不会影响其他分支的完成次数、在研任务
和已投入材料。回退一阶后，尚未完成的二阶研究暂停推进，直到前置重新满足（默认 1 次），而
二阶既有完成记录及已授予的生产配方权限保留。修改结果保存到控制器 NBT，并同步到已打开的
界面。

---

## 10. Java API

```java
// 变更操作必须在该控制器所属的服务端线程执行。
MatterResearchApi.start(controller, "molecularmanipulator:research/ae_foundation");
MatterResearchApi.setPaused(controller, "molecularmanipulator:research/sequence_array", true);
int rounds = MatterResearchApi.completionCount(controller, "molecularmanipulator:research/ae_foundation");
int effective = MatterResearchApi.setCompletionCount(controller, "molecularmanipulator:research/ae_foundation", 999L);
MatterResearchApi.setCompleted(controller, "molecularmanipulator:research/sequence_array", true);
MatterResearchApi.unlockAll(controller);
boolean unlocked = MatterResearchApi.isRecipeUnlocked(controller, "molecularmanipulator:molecular_center_controller");
var profile = MatterResearchApi.productionProfile(controller, fabricationRecipeHolder);
long parallel = profile.parallel();
int ticks = profile.ticks();
var definitions = MatterResearchApi.definitions(serverLevel);
```

类名为 `com.atir.molecularmanipulator.research.MatterResearchApi`。开发时对该模组 JAR 使用
`compileOnly`，运行时单独安装前置，不复制或嵌入这些类。研究 API 没有数值 ABI 协商方法；
独立的批量 API 保持 v1。

| 入口 | 契约 |
| --- | --- |
| `definitions(Level)` | 可用定义，按 `sort_order` 其次按 ID 排序；`required_mods` 未加载的定义会被过滤掉。 |
| `start(controller, id)`、`setPaused(controller, id, paused)` | 返回操作是否被接受。`start` 对已存在的任务执行恢复而不是失败；研究不存在、不可用、已满级、有待退款或不满足前置时返回 `false`。`setPaused(..., false)` 会重新检查结构、网络、退款与前置并补扣未支付部分，因此也可能返回 `false`。 |
| `completionCount`、`isCompleted` | 查询完成次数；`isCompleted` 表示至少完成 1 次，不是满级。 |
| `canUseRecipe`、`isRecipeUnlocked` | 供其他配方执行器使用的权限检查，调用方需自行调用以实施自己的规则。`canUseRecipe` 接收 `RecipeHolder<MatterFabricationRecipe>`。 |
| `productionProfile` | 返回该配方当前已完成分支的 `(parallel, ticks)`：取最高并行、最短耗时；无归属研究时并行为 `1`、耗时为基础值。 |
| `setCompletionCount`、`setCompleted`、`unlockAll` | 管理性修改，按当前最大深度钳制并结束受影响的在研任务，不返还已投入材料。负数会抛异常；次数为 `int`，`unlockAll` 返回处理过的定义数量。三者都不检查权限——由调用方负责。 |
| `prerequisitesMet`、`requiredPrerequisiteLevel` | 每个前置默认要求 1 次；前置不在传入的 `available` 列表中时返回 `false`，因此应传入 `definitions(level)`。Java 映射值 `0` 对应数据包中的 `"max"`。 |

在非属主服务端线程调用变更方法会抛出 `IllegalStateException`；读取类查询可在任意线程使用。
完整进度可通过 `controller.getResearch().save()` 读取。

Java 模组可用复制 API 生成修改后的定义：

```java
var parent = ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
var three = existingResearch.withPrerequisiteLevels(Map.of(parent, 3));
var full = existingResearch.withPrerequisiteLevels(Map.of(parent, 0)); // Java 的 0 对应数据中的 "max"
int required = MatterResearchApi.requiredPrerequisiteLevel(three, parentRecipeHolder);
boolean eligible = MatterResearchApi.prerequisitesMet(three,
        MatterResearchApi.definitions(serverLevel), controller.getResearch()::completionCount);
```

`withPrerequisiteLevels` 返回新定义，不修改旧定义与玩家进度；它**替换**而不是合并等级映射。
返回的新定义需通过正常配方注册或替换流程安装。原有的 9 参数与 10 参数构造器继续可用，
默认每个前置要求 1 次；完整规范构造器的最后一项是
`Map<ResourceLocation, Integer> prerequisiteLevels`。Java 映射接受正数（固定次数）或 `0`
（满级），而 JSON/KubeJS 接受正整数或字符串 `"max"`，不接受数值 `0` 和负数。

其他配方执行器需要自行调用权限与生产参数接口。

---

## 11. 世界特效

研究中的世界特效自动支持第三方模组、数据包和 KubeJS 添加的阶段，无须修改配方格式。三个
内置阶段分别使用冰青晶格、青绿方阵（金色点缀）、紫白轨道星图；其他阶段根据阶段 ID、控制器
位置、维度和研究轮次，从四种星图（晶格、方阵、轨道球、双螺旋）中稳定伪随机选择一种，因此
自定义阶段也可能落在三个内置形状上。同一轮在多人客户端、暂停/恢复和重载后保持相同样式，
下一轮重新选取，允许再次选中相同样式。

每个控制器最多同时展示四个研究星图，优先显示运行中的任务；此数量只限制视觉开销，不限制可
同时执行的研究数量。暂停、前置不满足、定义不可用、结构损坏、网络离线和供能不足时，星图变暗
并停止光脉冲；王冠弧数量随研究轮次增长（上限 3 圈），真实完成时播放 32 tick 的突破环与射线
动画。研究进度通过方块同步每 5 tick 传递一次，关闭控制器界面后依然显示；管理指令直接解锁
不会触发研究完成动画。客户端特效等级配置 `dynamic_effect_level`（0/1/2，默认 2）控制特效
细节：设为 `0` 会完全关闭星图，且只有客户端认为结构已成型时才会渲染。原有研究定义和公开
API 的签名保持兼容。
