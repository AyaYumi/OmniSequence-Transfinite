# Matter Fabrication Research / 物质构筑井研究 API

## English integration reference (2.0.1)

Minecraft 1.21.1 / NeoForge / Java 21. Required prerequisite: AppliedEnhancements
1.0.6+. See the [API index](README.md) and [batch-provider contract](omni-batch-provider-api.md).
Research progress belongs to each well controller, not to the player or a global network.

| Entry point | Contract |
| --- | --- |
| Data recipe `molecularmanipulator:matter_research` | Define research under `data/<namespace>/recipe/`; KubeJS uses `ServerEvents.recipes` and `event.custom`. |
| `MatterResearchApi.definitions(Level)` | Available definitions sorted by `sort_order`, then ID; optional-mod availability is respected. |
| `start(controller, id)`, `setPaused(controller, id, paused)` | Boolean result; mutations require the owning server thread. Materials are checked and extracted atomically from the controller's ME network. |
| `completionCount`, `isCompleted` | Query rounds; `isCompleted` means at least one completion, not maximum depth. |
| `canUseRecipe`, `isRecipeUnlocked` | Check research permission; custom executors must call the hook themselves. |
| `productionProfile` | Returns `(parallel, ticks)` for current completed branches; actual batch size also depends on materials, power and per-key capacity. |
| `setCompletionCount`, `setCompleted`, `unlockAll` | Administrative mutations; clamp to current maximum depth, end affected active research, do not refund spent research materials. `setCompleted(true)` means maximum depth. Callers enforce permissions. |
| `prerequisitesMet`, `requiredPrerequisiteLevel` | Default one completion per prerequisite; JSON/KubeJS `"max"` corresponds to Java map value `0`. |

The Java class is `com.atir.molecularmanipulator.research.MatterResearchApi`.
Use `compileOnly` and separate runtime installation; do not embed these classes.
Research API has no numeric ABI negotiation method. The separate batch API remains v1.

Built-in research takes **600 ticks / 30 seconds per round**, with nine rounds by
default. Custom definitions keep their own `duration`; omission means **1200 ticks**.
The two stage-two branches require one foundation completion by default. AdvancedAE
is required for the Omni branch. `unlocks` contains full **recipe IDs**, not item IDs.
Multiple completed branches grant the highest parallelism and shortest processing
time rather than multiplying bonuses. Research bonuses affect well production only.

For KubeJS research long fields, use decimal strings, especially above `2^53 - 1`;
`'9223372036854775807'` is valid while floating-point or exponent notation is not.
Use `neoforge:conditions` for optional-mod items: `required_mods` alone does not
prevent missing ingredients from failing during recipe decoding.

Pattern assemblies accept all registered AEKey input types through the optional
`ae_inputs` recipe field. Entries use AE2's `GenericStack.CODEC` format with `#t`
for the key type and `#` for the positive long amount. `ingredients` and `ae_inputs`
share a nine-entry limit. Existing item/fluid recipes remain compatible; recipes
using `ae_inputs` require assembly delivery. JEI and GuideME display these inputs.

| Generic input field | Meaning |
| --- | --- |
| `#t` | Registered AEKey type ID; AE2 items use `ae2:i`, fluids use `ae2:f`. Addons define their own IDs. |
| `#` | Raw amount per craft, an integer from 1 through 9223372036854775807. Item units are items; NeoForge fluid units are mB. |
| `id`, `components`, other fields | Decoded by the selected key type. Use that addon's codec rather than assuming it shares the item/fluid format. |

For example, `{"#t":"ae2:f","id":"minecraft:water","#":1000}` requests 1,000 mB of water.
Unlike the research fields above, `#` uses AE2's numeric `Codec.LONG`, not the
research decimal-string codec. For values beyond JavaScript's exact integer range,
use an integer literal in a data-pack JSON file or construct the resource with Java
`new GenericStack(key, longAmount)`. Do not first convert it through a JavaScript number.
Generic keys match exactly, including components; normal `ingredients` retain their
Ingredient/tag matching. Repeated or overlapping requirements are additive.

Java recipes use `new MatterFabricationRecipe(ingredients, results, fluidInput,
fluidResult, aeInputs, processingTime, aePerTick, requiresResearch)` with
`List<GenericStack> aeInputs`. The previous constructors remain available and default
to no generic inputs. Outputs still use `results` and `fluid_result` (up to two item
results and one fluid result); this update does not introduce generic output fields.

Recipe lookup indexes complete outputs, including amounts, and caches research
definitions and recipe ownership. Recipe snapshot replacement rebuilds the index;
controller completion counts remain live. Assemblies own persistent AEKey input,
output and refund buffers.
Started work stores its processing snapshot. New or unstarted work uses current
recipe permissions and bonuses. Finished products and refunds return to ME;
blocked transfers stay in the assembly. The controller's manual port workflow
remains separate. Removing one block does not collect other assemblies' inventories.

Pattern lookup includes the full expected output and quantity. Identical or
proportional inputs producing different outputs remain separate, including API
batches and mixed queues after save/reload. Two crafts of `10A + 10B -> C` produce
`2C` even if `20A + 20B -> D` also exists. The same applies to `10A + 10B -> D`.

Known 2.0.1 limitations: overlapping alternatives with identical outputs can select
a different recipe during batch splitting, changing time and power. A reload that
introduces an earlier matching recipe can leave an existing queue waiting even
while its original recipe still exists. These cases remain unresolved; neither
output indexing nor API admission should be treated as a fix for them.

OP level 2 commands: `/matter_research unlock_all`, `complete <id> <true|false>`
and `set <id> <count>`. Append `x y z` for an explicit loaded controller, or aim
at one within 16 blocks. Console use requires coordinates. Java hooks do not
inherit command permission checks.

Complete field tables, KubeJS examples and administration details follow below.

## 中文：研究与生产契约

研究进度属于控制器。新建时为 0 阶，每个研究首次完成解锁其配方，之后可重复进行深度研究，提高这些配方的并行上限和加工速度。默认每个研究共 9 次（包含首次解锁）；不同研究分支可以同时运行。

## 内置研究与解锁链

| 研究 ID（前缀 `molecularmanipulator:`） | 每次研究时间 | 研究功耗 | 首次解锁内容 |
| --- | --- | --- | --- |
| `research/ae_foundation` | 600 tick / 30 秒 | 256 AE/t | 7 条 AE 材料配方，以及 27 条二阶研究材料和中间材料配方 |
| `research/sequence_array` | 600 tick / 30 秒 | 512 AE/t | 构序阵列 6 类部件，以及分子构序重写阵列、装配矩阵构序重写核心 |
| `research/omni_computation` | 600 tick / 30 秒 | 1024 AE/t | 万物演算 10 类部件 |

上述 30 秒适用于内置研究的首次解锁及后续每轮深度研究。KubeJS/数据包的 `duration` 仍按 tick 自由配置，省略时仍默认 1200 tick；已开始的研究保留开工时的耗时快照，新开始的轮次使用更新后的定义。

一阶的新增配方 ID 为 `molecularmanipulator:fabrication/research_materials/<模组 ID>/<物品名>`，覆盖两条二阶研究消耗的 17 种材料和机器，并补齐相关中间材料、量子注入液配方。数量 27 包含这些中间配方。加工方法基于整合包实际安装的 ExtendedAE、AdvancedAE 配方；处理器使用本井直接加工。它们在一阶首次完成后开放，获得一阶的深度研究加成。

无 AdvancedAE 时，万物演算分支及 AdvancedAE 材料配方不加载，界面也不列出缺失配方。默认一阶完成 **1 次**即可开始二阶；之后可继续深度研究提高一阶配方的生产能力。前置完成次数可按研究逐项配置，支持固定次数和要求满级。构筑井控制器、5 类结构件和 4 类输入输出口使用 AE 原版材料制作，不受研究门槛影响；样板总成需要一阶首次完成后在构筑井内加工制作。

分子构序重写阵列（`molecular_manipulator`）和装配矩阵构序重写核心（`assembler_matrix_molecular_core`）已移入二阶构序阵列分支：工作台配方改为构筑井加工，原材料种类与数量保持不变，基础加工均为 400 tick、512 AE/t。该分支首次完成后解锁这两条配方，后续深度研究为它们提供同分支的速度与并行加成。

## 默认九次进度

每轮费用独立收取，不是从上一轮补差额。`并行`表示一次加工可执行多少份完整配方。

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

加工时间至少 1 tick。并行与速度加成只作用于该研究 `unlocks` 列出的构筑井配方，不改变研究自身耗时。不同研究若解锁同一配方，取最高并行、最短耗时，不将加成相乘。

## 实时库存、批量扣料与保存

- 页面每 5 个服务器 tick 同步所选研究的当前 AE 实际库存；开始研究时再次直接读取存储提供者，重新校验并批量提取。研究仅消耗控制器所在 AE 网络中的物品，不取玩家背包和接口库存。
- 所有材料必须齐全才能开始。重叠的物品/标签要求通过联合分配校验，同一份库存不会重复计入两个需求，也不需要手工调整材料声明顺序。
- 若提供者在核验与实际提取之间发生变化，研究不启动，退回已提取材料；网络暂不接收的退款由控制器保存，网络恢复后重试，退款未完成前不接受新研究。
- 正常研究开始时扣完费用，后续计时不再取材。暂停、断网、缺电、结构损坏或搭建/拆除期间保留材料和进度。恢复研究不重复收费。
- 所有前置研究都必须达到各自要求的完成次数，默认 1 次。`prerequisite_levels` 可覆盖单项要求；`"max"` 表示前置研究当前的最高次数。正在进行的后续研究若因指令回退或配置变更不再满足要求，会保留材料与进度并等待条件恢复，不会继续扣料。
- 同一研究同时至多运行一轮，不同分支独立计时、耗能。研究可与普通生产同时进行，共享网络供电。
- 已开始任务保存材料、轮数、深度表、研究时间和功耗快照；重载定义不重新收费。完成次数按研究 ID 保存，加工加成和解锁列表从当前定义计算。缩短深度表时保留原完成次数，以新的最后一项计算加成。
- 旧版本的“已完成”迁移为完成 1 次；旧部分取材任务暂停，点击继续时一次性补齐尚未支付的部分，保留原进度。
- 控制器存档、正常掉落和 AE 拆卸物品均保存研究次数、在研任务与待退款。记忆卡不复制这些内容。缺少依赖导致无法解码的任务/退款保留原始数据，兼容环境重载后可恢复。

## 生产与 AE 大批量

手动接口加工与样板总成都使用同一研究加成。手动物品缓存按实际库存、输出空间和供电计算本轮份数；不会把 long 数量塞进普通 ItemStack。

样板总成实现公开 `OmniBatchCraftingProvider` API，支持本模组演算系统的 long 批次。输入支持所有已注册的 AEKey 类型，配方通过下述 `ae_inputs` 声明额外资源。输入与输出以 AE Key + long 持久保存，处理工作量取决于材料种类数，不逐份循环。AE 普通单份投料也使用同一持久批次，可在开始加工前合并同配方投料。

样板总成自行持有输入、输出和退款缓存，产物与待退回原料自动写回其 ME 网络；网络不接收时保留在总成内，不转存到手动输出口。控制器保留的旧批次兼容路径与手动接口加工使用各自缓存。输出堵塞或保存重载不会丢弃已接收材料，单独拆除控制器不会收走其他总成的库存。

投料时按完整产物及数量查找候选配方，并复用研究定义与配方归属索引；同产物候选仍按配方管理器的顺序匹配，输入逐次完整校验。索引随配方管理器的配方快照替换自动重建，支持数据包重载及 `replaceRecipes`。各控制器的研究完成次数实时读取，解锁、撤销和存档恢复不会沿用其他控制器或旧状态的权限。

并行是上限：实际批次仍受材料、供电、输出接收能力和每种 AE Key 的 long 数量上限限制。例如每份输出 16 个相同物品时，单批份数最多 `Long.MAX_VALUE / 16`。功耗按原配方每 tick 功耗乘本批份数计收。原生测试已通过公开 API 实际接收并在 1 tick 结算 `Long.MAX_VALUE` 份 1 输入 / 1 输出、0 功耗配方；此测试验证数值和结算能力，不代表默认有免费材料或供电。

## JEI 与输入输出口

JEI 配方顶部标注负责开放此配方的研究阶段与名称。鼠标悬停可查看完整研究条件；多个研究授予同一配方时会全部列出。没有研究限制的配方显示“基础配方 · 无需研究”。底部时间和功耗为基础参数，深度研究加成由控制器实际应用。

| 接口 | 控制方式 |
| --- | --- |
| 物品输入口、流体输入口 | 已绑定控制器且 AE 在线时，点击“全部退回 AE”将本接口缓存退回该网络；接收不下的保留 |
| 物品输出口、流体输出口 | “自动输出”默认关闭；开启后每 5 tick 向已选方向的相邻容器转移，按实际接收量扣除缓存 |
| 输出方向 | 上、下、北、南、西、东独立切换，新接口六方向默认全部关闭；开启后按钮固定浅蓝色并带方括号，关闭时灰色，鼠标悬停为青绿色，可多选；按钮显示该方向相邻方块的图标，悬停查看名称，方块改变后自动更新；使用世界方向，连接相邻容器朝向本接口的面 |
| 样板总成 | 显示 AE 连接状态和样板占用数；支持命名，窗口缩放保留尚未保存的名称 |

流体输入口、流体输出口的四个缓存槽都支持手动双向存取：鼠标拿着流体容器右键指定槽，有流体就尝试倒入，空容器就尝试装出；每次处理一个容器。单个水桶会变为空桶，单个空桶会变为水桶，创造模式也进行实际容器交换。堆叠空桶取水时，水桶放进背包，鼠标上空桶减一；若背包放不下结果，操作不生效。槽满、流体不兼容或不足以装满水桶时不会吞掉容器或流体。左键保留输入口倒入、输出口取出的原操作。

输出缓存的自动转移无需保持界面打开。未选任何方向时停止输出；相邻区块未加载时不强制加载。物品和流体输出仅发送到相邻容器，不直接丢到世界中。已保存的方向设置不会因更新默认值而重置。输出开关、方向随世界保存，并保留在 AE 拆卸设置和控制器分批拆解打包的接口物品中。

## 数据定义与 KubeJS

研究是普通配方类型 `molecularmanipulator:matter_research`，位于 `data/<namespace>/recipe/<path>.json`。KubeJS 使用 `ServerEvents.recipes` 的 `event.custom` 添加或替换，不需要额外适配插件。

| 字段 | 含义 |
| --- | --- |
| `title` | 名称或翻译键 |
| `stage` | 显示阶数，默认 1；先后关系由前置列表决定 |
| `sort_order` | 导航顺序，默认 0 |
| `prerequisites` | 前置研究 ID 列表，各项默认要求完成 1 次；避免自引用和循环 |
| `prerequisite_levels` | 可选映射 `{研究ID: 所需完成次数或 "max"}`，覆盖单项门槛；映射中的 ID 也会自动加入前置列表 |
| `ingredients` | 基础研究耗材，每项 `{ingredient: {item: ...} 或 {tag: ...}, count: 正 long}` |
| `duration` | 每次研究时间，至少 1 tick，默认 1200 |
| `ae_per_tick` | 研究期间功耗，有限非负数，默认 256 |
| `required_mods` | 显示、运行所需全部模组 ID |
| `unlocks` | 解锁并获得加成的完整加工配方 ID，不是物品 ID |
| `depths` | 每轮的费用和完成后的总加成；数组长度就是研究总次数，省略采用默认 9 次 |

前置配置示例（JSON 与 KubeJS 的 `event.custom` 均可直接使用）：

```json
"prerequisites": ["molecularmanipulator:research/ae_foundation"],
"prerequisite_levels": {
  "molecularmanipulator:research/ae_foundation": 3,
  "kubejs:another_research": "max"
}
```

这表示 AE 材料构筑研究至少完成 3 次，并且 `kubejs:another_research` 满级。映射中未设置的已有前置默认要求 1 次；`prerequisites` 可以省略，只写映射也会建立依赖。`"max"` 会随前置研究的 `depths` 长度变化。数值门槛不会被自动调低，配置时应确保不超过该前置的可完成次数。缺失或依赖模组未加载的前置视为不满足。

这里的“级”指累计完成研究次数，不是显示用的 `stage` 阶段编号。默认两条二阶分支不需要一阶满级。配置热重载后，开始、继续和计时校验都使用当前门槛；已有已付款任务不会重新收费。界面的分母会显示实际所需次数，达到要求后显示绿色。

`depths` 每一项的字段：

| 字段 | 含义 |
| --- | --- |
| `material_multiplier` | 本轮费用 = 基础 `ingredients` ×该倍率，默认 1 |
| `ingredients` | 可选，提供时**完整替代**本轮材料，不再乘倍率；`[]` 表示免费一轮 |
| `parallel` | 本轮完成后的总并行上限，必填正 long |
| `speed_multiplier` | 用原配方时间除该值并向上取整，默认 1 |
| `processing_ticks` | 大于 0 时固定加工 tick 数，优先于速度倍率；0/省略采用速度倍率 |

所有 long 字段（材料 `count`、`material_multiplier`、`parallel`、`speed_multiplier`）可写十进制字符串，推荐始终加引号。超过 JavaScript 安全整数 `2^53 - 1` 时必须使用字符串，例如 `'9223372036854775807'`，不能用浮点数或 `'9.22E18'` 代替。费用乘法超出 long 上限时拒绝开始，不会变成负数或免费研究。

下面示例放入整合包 `kubejs/server_scripts/matter_research.js`，新增只有 3 次的研究，逐轮改变材料、并行和速度：

```javascript
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:matter_research',
    title: '石材深度研究', stage: 3, sort_order: 100,
    prerequisites: ['molecularmanipulator:research/ae_foundation'],
    ingredients: [{ingredient: {item: 'ae2:fluix_crystal'}, count: '32'}],
    duration: 1200, ae_per_tick: 128,
    unlocks: ['kubejs:stone_processing'],
    depths: [
      {parallel: '4', speed_multiplier: '1'},
      {material_multiplier: '3', parallel: '1024', speed_multiplier: '8'},
      {
        ingredients: [
          {ingredient: {item: 'ae2:fluix_crystal'}, count: '512'},
          {ingredient: {item: 'minecraft:diamond'}, count: '64'}
        ],
        parallel: '9223372036854775807', processing_ticks: 1
      }
    ]
  }).id('kubejs:stone_research')

  event.custom({
    type: 'molecularmanipulator:matter_fabrication',
    ingredients: [{ingredient: {item: 'minecraft:cobblestone'}, count: 1}],
    results: [{id: 'minecraft:stone', count: 1}],
    processing_time: 200, ae_per_tick: 64, requires_research: true
  }).id('kubejs:stone_processing')
})
```

修改内置研究时，先 `event.remove({id: 'molecularmanipulator:research/ae_foundation'})`，然后注册同 ID 的完整新定义。可从 JAR 的 `data/molecularmanipulator/recipe/research/` 取出原 JSON 作为模板，保留想继续解锁的 `unlocks`。保持研究 ID 即可沿用完成次数。`depths` 改成几项就有几次研究，可完全替换每轮成本与加成。

涉及可选模组物品时，顶层还须添加 `neoforge:conditions`，使缺少模组时跳过 Ingredient 解析：

```json
"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "advanced_ae"}]
```


## 管理指令

需要开启作弊或具有 OP 2 级权限。省略坐标时，准心需指向 16 格以内的物质构筑井控制器；也可在指令末尾加 `x y z`，支持 `~` 相对坐标。服务器控制台必须指定坐标，目标区块必须已经加载。指令仅操作目标控制器，不要求结构成型或 AE 在线，也不扣材料。

| 指令 | 效果 |
| --- | --- |
| `/matter_research unlock_all` | 将目标控制器的所有可用研究直接设为各自满级，包括 KubeJS 新增研究 |
| `/matter_research complete <研究ID> true` | 将单项研究设为满级 |
| `/matter_research complete <研究ID> false` | 将单项研究设为未完成，即 0 次 |
| `/matter_research set <研究ID> <次数>` | 设置单项完成次数；0 表示未完成，超过上限自动设为最高次数 |

例如：

```mcfunction
/matter_research unlock_all
/matter_research set molecularmanipulator:research/ae_foundation 5
/matter_research complete molecularmanipulator:research/sequence_array true
/matter_research complete molecularmanipulator:research/sequence_array false
/matter_research set molecularmanipulator:research/omni_computation 999 100 64 200
```

最后一条将当前维度坐标 `100 64 200` 的控制器之万物演算研究设为最高次数，默认 9 次。研究 ID 支持 Tab 补全。负数被拒绝，计数参数接受 0 至 `9223372036854775807`；实际保存值会按该研究当前 `depths` 长度钳制。未加载 AdvancedAE 时，万物演算不会出现在补全和全部解锁列表中，单独指定也会提示不可用。

设置某项完成状态或次数会结束该项正在进行的研究，已用于研究的材料不返还，也不会额外扣料；全部解锁会结束所有被设为满级的在研任务。单项设置不会清空其他分支的完成次数、在研任务和已投入材料。回退一阶后，尚未完成的二阶研究暂停推进，直到一阶重新满足配置要求（默认 1 次）；二阶既有完成记录及已授予的生产配方权限保留。修改结果保存到控制器 NBT，并同步到已打开的界面。

## 权限与 Java API

`matter_fabrication` 配方的 `ingredients`（计数物品，每项 1–64 个）与 `ae_inputs`（通用 AEKey）合计最多 9 项，另支持 `fluid_input`、最多 2 种物品输出和 1 种流体输出。`requires_research` 默认 `false`。只要被研究的 `unlocks` 引用，就必须完成该研究；多个研究引用时任意一个完成即可。`requires_research: true` 在无人授予权限、研究被移除或缺少依赖时保持锁定。

`ae_inputs` 默认为空，旧配方无需修改。每项采用 AE2 `GenericStack.CODEC` 格式：`#t` 是已注册的 AEKey 类型 ID，`#` 是每份配方所需的原始 long 数量（1–9223372036854775807），其余字段由对应类型的 Codec 决定。以下示例只使用 AE2 自带类型，可直接作为数据包配方：

```json
{
  "type": "molecularmanipulator:matter_fabrication",
  "ae_inputs": [
    {"#t": "ae2:i", "id": "minecraft:diamond", "#": 2},
    {"#t": "ae2:f", "id": "minecraft:water", "#": 1000},
    {"#t": "ae2:f", "id": "minecraft:lava", "#": 1000}
  ],
  "results": [{"id": "minecraft:obsidian", "count": 1}],
  "processing_time": 200,
  "ae_per_tick": 64
}
```

第三方气体、化学品等资源使用其 AE2 兼容模组注册的类型 ID 与序列化字段；数量单位也由该类型定义。Java 可用 `new GenericStack(key, amount)` 填充 `MatterFabricationRecipe` 的 `aeInputs`，或用 `GenericStack.CODEC` 导出准确格式。资源按完整 AEKey 精确匹配，重复键与 `ingredients`/`fluid_input` 的重叠需求会累加，不能重复抵扣同一份材料。

`#` 使用 AE2 的数值 `Codec.LONG`，与研究字段支持的十进制字符串不同，不能直接沿用研究字段的字符串写法。超过 JavaScript 精确整数范围 `2^53 - 1` 时，使用数据包 JSON 中的整数字面量，或通过 Java 的 `new GenericStack(key, longAmount)` 构造，避免先经 JavaScript 浮点数转换。

Java 完整构造器为 `new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult, aeInputs, processingTime, aePerTick, requiresResearch)`，其中 `aeInputs` 为 `List<GenericStack>`。旧构造器保持兼容，通用输入默认为空。产物仍由 `results` 和 `fluid_result` 定义，本次没有增加通用输出字段。

包含 `ae_inputs` 的配方通过样板总成投料；普通物品/流体接口不能代替通用 AE 缓存。JEI 与指南显示这些原料，总成仍要求处理样板的完整输入、输出匹配实际构筑井配方。新增输入不会放宽研究、供电或产物校验。

匹配包含样板的完整预期产物及数量。原料相同或成比例、产物不同的配方会分别执行：`10A + 10B -> C` 下单两份仍产出 `2C`，不会因同时存在 `20A + 20B -> D` 或 `10A + 10B -> D` 而改产 D；批量投料、交错排队及存档重载均已验证。

2.0.1 尚存的边界：产物相同、可替代原料范围重叠时，拆分出的原料可能重新匹配另一条配方并改用其耗时和能耗；重载时新增更靠前的匹配配方，可能导致已有队列等待，即使原配方仍存在。这两类问题尚未修复。

权限默认作用于物质构筑井，不全局拦截其他机器；两种高级多方块的部件配方已改为构筑井加工。已经建成的高级机器仍能使用。

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

类名为 `com.atir.molecularmanipulator.research.MatterResearchApi`。`start` 和 `setPaused` 返回操作是否被接受；`isCompleted` 保留兼容语义，至少完成 1 次即为 true。`setCompletionCount` 返回自动限制后的次数；`setCompleted(true)` 表示设为满级，`false` 表示归零。三个管理 API 与指令具有相同的在研任务处理规则。完整进度可通过 `controller.getResearch().save()` 读取。其他配方执行器需要自己调用权限和生产参数接口。

Java 模组可使用不可变研究定义的复制 API 配置前置：

```java
var parent = ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
var three = existingResearch.withPrerequisiteLevels(Map.of(parent, 3));
var full = existingResearch.withPrerequisiteLevels(Map.of(parent, 0)); // Java 的 0 对应数据中的 "max"
int required = MatterResearchApi.requiredPrerequisiteLevel(three, parentRecipeHolder);
boolean eligible = MatterResearchApi.prerequisitesMet(three,
        MatterResearchApi.definitions(serverLevel), controller.getResearch()::completionCount);
```

将返回的新定义通过正常配方注册/替换流程安装；方法不会修改旧定义或玩家进度。原有 9 参数和 10 参数构造器继续可用，默认前置为 1 次；新的完整构造器最后一项是 `Map<ResourceLocation, Integer> prerequisiteLevels`。Java 映射允许正数（固定次数）或 0（满级），JSON/KubeJS 则使用正整数或字符串 `"max"`，不接受数值 0 和负数。

数据包及 KubeJS 配方重载更新后续研究费用和当前配方加成。正在处理的 AE 批次保留开工时的产物、耗时和功耗快照，即使配方定义被移除，也会继续完成。尚未开工的排队批次保存原料所有权和配方 ID，按当前配方、权限与参数重新检查，不保留旧加工参数快照；无法继续加工时，原料仍留在总成内，可退回待加工原料。

研究中的世界特效自动支持第三方模组、数据包和 KubeJS 添加的阶段，无须修改配方格式。三个默认阶段分别使用冰青晶格、青金方阵、紫白轨道星图；其他阶段根据阶段 ID、控制器位置、维度和研究轮次，稳定伪随机选择四种星图之一（另含双螺旋）。同一轮在多人客户端、暂停/恢复和重载后保持相同样式，下一轮重新选取，允许再次选中相同样式。

世界渲染最多同时展示四个研究星图，优先显示运行中的任务；此数量只限制视觉开销，不限制可同时执行的研究数量。暂停、前置不满足、网络离线和供能不足时，星图变暗并停止光脉冲。研究进度通过方块同步传递，关闭控制器界面后依然显示；管理指令直接解锁不会触发研究完成动画。原有研究定义和公开 API 的签名保持兼容。
