# 配方权限：用星算研究控制制作

整合包作者可用星算研究控制配方的制作许可。玩家仍能查看配方；没有许可时，服务器拒绝相应的制作操作。
遇到默认锁时，在本队已成型、在线且实际 L3 的星算引擎配方树中，主动解锁免费的单方块集成；
设备自身的井内研究仍须完成。安装与入门见[玩家指南](../README.zh-CN.md)，正常游玩无需编写声明。

当前对应 OmniSequence 20.3.0.1，Minecraft 1.20.1 / Forge、Java 17。

- 配方默认开放，只锁显式命中的配方。随模组只附带 1 份默认锁声明，目标只有单方块分子构序重写阵列与单方块超限算枢的配方；需要解锁 L3「单方块集成」（`molecularmanipulator:material`）研究。它也是唯一默认研究，情报费用为 0 bit、无前置，仍须玩家在引擎的配方树中主动点击解锁。物质构筑井等多方块控制器不在默认锁范围内，其他配方不会因此自动受限。
- 配方锁在下文已覆盖的手动制作、自动机器和 ME 执行入口统一检查，不删除配方或隐藏 JEI。共享配方目录、JEI 展示和样板编码不授予制作许可；「全局」不代表任意第三方机器都已适配。
- 研究与锁都使用普通配方声明：`molecularmanipulator:star_research` 定义研究，`molecularmanipulator:recipe_lock` 指定受限配方。KubeJS 只用普通 `event.custom(...)` 生成声明，权限检查由 Java 服务端执行。

研究购买、内置节点、前置校验及永久存档见[星算研究节点文档](star-research-nodes.zh-CN.md)。

研究要求仅在 JEI 的具体配方页显示，按当前展示配方的真实 ID 查询，提示为「需要研究 XXXX 才能解锁该合成」，包含构筑井的物品、流体和自定义 AE 资源输出槽。研究名取自当前 `star_research` 定义，支持本地化标题和数据包／KubeJS 的自定义名称；`all`、`any` 和多条锁分别按「且」「或」及括号显示真实组合要求。背包和 JEI 物品列表的普通物品悬浮不附加研究要求，也不按产物反查其他配方。锁声明删除或重载后提示同步更新。

购买后仍可查看研究要求；当前团队或在线 ME 网络是否有制作许可，由服务端检查。

Forge JEI 参考为 **15.49.0.188**（Minecraft 1.20.1）。提示钩子覆盖该版本的 `RecipeSlot.getTooltip(ITooltipBuilder, ITypedIngredient)`，也为 JEI 15.74 改用的 `addTooltip(ITooltipBuilder)` 入口提供适配，均在输出槽原生提示回调结束后追加研究要求。其他版本仍需核对实际入口；源分支 JEI 19.x 的兼容结论不适用于本分支，提示存在也不代表目标机器已有执行适配。

## 1. 先跑通一个 KubeJS 示例

安装 KubeJS 后，将脚本保存为 `kubejs/server_scripts/recipe_permissions.js`。它新增「自定义研究」，并用真实原版配方 ID 演示两种锁法，不修改材料或产物。研究会进入星算引擎的配方树，供玩家查看条件并购买；添加研究本身不会自动锁住配方。

```js
ServerEvents.recipes(event => {
  // 此声明的 ID 就是研究节点 ID，供 prerequisites / requires 引用。
  event.custom({
    type: 'molecularmanipulator:star_research',
    title: '自定义研究',
    required_level: 4,
    information_cost: 1024,
    prerequisites: ['molecularmanipulator:material']
  }).id('pack:custom')

  // 例一：需要自定义研究；同时演示全部五种匹配字段。
  event.custom({
    type: 'molecularmanipulator:recipe_lock',
    requires: ['pack:custom'],
    mode: 'all',
    match: {
      recipe_ids: [
        'minecraft:iron_ingot_from_smelting_iron_ore',
        'minecraft:iron_ingot_from_smelting_raw_iron',
        'minecraft:iron_ingot_from_blasting_iron_ore'
      ],
      recipe_id_regex: [
        'minecraft:iron_ingot_from_smelting_.*',
        'minecraft:iron_ingot_from_blasting_.*'
      ],
      recipe_types: ['minecraft:smelting', 'minecraft:blasting'],
      namespaces: ['minecraft'],
      exclude_ids: ['minecraft:iron_ingot_from_smelting_raw_iron']
    }
  }).id('pack:recipe_locks/iron_processing')

  // 例二：自定义研究或单方块集成，拥有任意一个即可制作活塞。
  event.custom({
    type: 'molecularmanipulator:recipe_lock',
    requires: ['pack:custom', 'molecularmanipulator:material'],
    mode: 'any',
    match: {
      recipe_ids: ['minecraft:piston']
    }
  }).id('pack:recipe_locks/piston')
})
```

保存后执行 `/reload`，确认服务器日志没有声明加载错误。打开本队已绑定、成型且 ME 在线的星算引擎配方树，先在核心实际 L3 时主动解锁零费用的「单方块集成」，再在实际 L4、拥有 1024 星光情报时选择并购买「自定义研究」。标题也可改为客户端语言文件中的翻译键。

新增研究与默认研究使用同一列表，总数超过五项时可在 GUI 中滚动查看并购买。自定义研究同样按 ID 保存为本队永久进度，已买节点不会重复扣费；同网借来的许可不能充当前置。

也可在本队核心中心 8 格内用下列普通玩家命令，检查条件与 GUI 相同，并非免费授予：

```mcfunction
/starresearch list
/starresearch unlock molecularmanipulator:material
/starresearch unlock pack:custom
```

| 配方 ID | 此示例的要求 |
| --- | --- |
| `minecraft:iron_ingot_from_smelting_iron_ore` | 自定义研究（`pack:custom`） |
| `minecraft:iron_ingot_from_blasting_iron_ore` | 自定义研究（`pack:custom`） |
| `minecraft:iron_ingot_from_smelting_raw_iron` | 被本条规则排除，未增加限制 |
| `minecraft:piston` | 自定义研究 或 单方块集成 |

因为本例的自定义研究以「单方块集成」为前置，活塞在解锁「单方块集成」后就能制作；`any` 演示任选其一，无需再买自定义研究。同时加载其他示例的活塞锁时，多条锁仍会叠加。

没有列入的其他铁锭配方不会因为产物相同而自动受限；其他锁声明仍可给这些配方叠加要求。熔炉等自动机器使用其绑定团队的许可，不能只看打开机器的玩家是否买过研究。

不用 KubeJS 也可用数据包：在 `data/pack/recipes/recipe_locks/iron_processing.json` 中写入相同的锁对象即可。本分支使用复数 `recipes`（数据包格式 15），不能照搬 1.21 的单数目录；对应声明 ID 为 `pack:recipe_locks/iron_processing`。研究也需单独声明，例如 `data/pack/recipes/custom.json` 对应节点 ID `pack:custom`。

## 2. 怎样选配方、怎样算满足

### 三种 ID 不要混用

- 目标配方 ID：`match.recipe_ids` / `exclude_ids` 指向正在制作的配方，不是物品 ID。例如铁矿石熔炼的配方 ID 是 `minecraft:iron_ingot_from_smelting_iron_ore`，不能用产物 `minecraft:iron_ingot` 代替来锁住所有铁锭来源。
- 研究节点 ID：研究声明的 `.id('pack:custom')` 就是节点 ID（Forge `Recipe.getId()`）；`requires` 与研究的 `prerequisites` 引用它。标题不是 ID。研究费用的字段名是 `information_cost`，不是 `cost`。
- 锁声明 ID：锁的 `.id('pack:recipe_locks/iron_processing')` 只用于识别、删除或替换这条规则，不是要购买的节点，也不是目标制造配方。

### 锁的字段

| 字段 | 写法与含义 |
| --- | --- |
| `requires` | 必填，非空的研究节点 ID 字符串数组。 |
| `mode` | 可省略，默认 `all`：需要 `requires` 中全部节点；`any`：该条规则中任意一个节点即可。只接受这两个值。 |
| `match` | 必填，选择受限配方。下面四个正向字段至少有一个非空；不能只写 `exclude_ids`。 |

`match` 内所有字段都是数组；省略或空数组表示该字段不增加筛选条件：

| 字段 | 匹配方式 |
| --- | --- |
| `recipe_ids` | 精确配方 ID，例如 `['minecraft:piston']`。 |
| `recipe_id_regex` | 字符串数组，不是单个字符串，也不是 JavaScript `/.../` 正则对象。使用 Java 正则，对含命名空间的完整 `namespace:path` 做全匹配（`matches()`），不是搜索子串。例如 `minecraft:iron_ingot_from_.*`；只写 `iron_ingot` 不会命中完整 ID。需要反斜杠时还须遵守 JS / JSON 字符串转义。 |
| `recipe_types` | 配方类型 ID，例如 `minecraft:crafting`、`minecraft:smelting`、`minecraft:blasting`、`ae2:inscriber`。这里不是序列化器 ID；有序/无序合成均属于 `minecraft:crafting`，不要填 `minecraft:crafting_shaped`。 |
| `namespaces` | 配方 ID 的命名空间，如 `['minecraft', 'ae2']`，不是产物来自哪个模组。 |
| `exclude_ids` | 从本条规则排除的精确配方 ID，优先于本条所有正向条件；不豁免其他规则。 |

三条组合规则：

1. 同一字段里的多个值是 OR（任选其一）：列出两个配方 ID，就选择其中任意一个。
2. 多个非空正向字段是 AND（同时满足）：同时填 ID、正则、类型和命名空间时取交集，再应用排除。若想选「一组 ID 或一类配方」，应拆成分别匹配的声明。
3. 同一配方命中多条锁时，各条锁也是 AND。`mode: 'any'` 只影响它自己那一条的 `requires`。例如一条要求 `A 或 B`，另一条要求 `C`，最终就是 `(A 或 B) 且 C`；规则顺序不会让后加的宽松规则覆盖旧锁。

缺失或无效研究会让对应锁拒绝制作。 `requires` 引用的节点必须在当前目录中有有效定义；缺前置、自引用、循环前置及依赖错误节点的下游节点都无效。即使永久存档里已有该 ID，也不能替代当前定义。哪怕是 `any`，只要它引用的节点中有未定义或无效项，整条规则仍无效，不会靠另一个有效选项放行。影响只限于命中该锁的配方。

空 `requires`、非法 `mode`、非法正则或没有正向条件等属于声明加载错误，应检查日志并修正，不要把写坏声明当作可靠的禁用方式。规则和研究定义在成功 `/reload` 后更新；配方匹配结果会重建，机器不会因已有配方缓存而永久保留旧许可。

## 3. 唯一默认锁：两种单方块设备与 L3 单方块集成

声明位于 [`data/molecularmanipulator/recipes/recipe_locks/material.json`](../src/main/resources/data/molecularmanipulator/recipes/recipe_locks/material.json)，完整声明 ID 保持为 `molecularmanipulator:recipe_locks/material`。它要求 `molecularmanipulator:material`（单方块集成 / Single-Block Integration），只列下表两个精确配方 ID；省略 `mode`，因此使用默认 `all`：

| 目标配方 ID（仅以下两条） | 单方块设备 | 仍须完成的井内研究 |
| --- | --- | --- |
| `molecularmanipulator:molecular_manipulator` | 分子构序重写阵列 | Ⅲ阶：构序工程 |
| `molecularmanipulator:transfinite_compute_nexus` | 超限算枢 | Ⅵ阶：超限集成工程 |

这层星算许可与上述井内研究同时生效，不能相互替代。配方材料、加工费用及其他条件保持不变；超限算枢配方仍仅在安装 AdvancedAE 时加载。

「单方块集成」沿用原 `material` ID 与永久 bit 1，默认规则仍是：核心实际生效等级须达到 L3、`information_cost: 0`、`prerequisites: []`。不需要 `foundation`，也不扣星光情报；仍须玩家在成型、在线的本队引擎中主动点击解锁，不能只搭到 L3 就直接制作。L1、L2 仍不能解锁；L3 是购买门槛，已购买后的制作许可按下节规则判断。受限配方的提示使用当前名称「单方块集成」。

多方块控制器均不在默认锁范围内： 物质构筑井控制器（`molecularmanipulator:matter_fabrication_controller`）、构序阵列控制器（`molecularmanipulator:molecular_center_controller`）和万物演算核心控制器（`molecularmanipulator:omni_computation_controller`）。建井不以购买这项 L3 许可为前提；整合包追加的材料链或研究条件仍须自行检查是否相互卡住。

默认研究列表只有「单方块集成」；其余四项的保底定义已移除，但旧 ID、存档位和已买进度不清除、不重排。整合包可重新声明原 ID 来恢复相应研究，或添加自己的新 ID；它们都会进入同一个 GUI。

覆盖研究请用 `.id('molecularmanipulator:material')`；`event.remove` 不能删掉这一项内置保底定义，删除覆盖只会恢复 L3、0 费用、空前置。移除配方锁则使用 `event.remove({ id: 'molecularmanipulator:recipe_locks/material' })`。替换锁时也先删除同一声明 ID，再写入新规则。不要删除目标制造配方来模拟解锁，也不要仅追加一条更宽松的规则；原规则仍会叠加生效。下面的独立脚本覆盖默认锁声明，仍只锁上表两条单方块配方并要求「单方块集成」，可据此调整：

```js
ServerEvents.recipes(event => {
  event.remove({ id: 'molecularmanipulator:recipe_locks/material' })
  event.custom({
    type: 'molecularmanipulator:recipe_lock',
    requires: ['molecularmanipulator:material'],
    mode: 'all',
    match: {
      recipe_ids: [
        'molecularmanipulator:molecular_manipulator',
        'molecularmanipulator:transfinite_compute_nexus'
      ]
    }
  }).id('molecularmanipulator:recipe_locks/material')
})
```

若只需取消默认锁，保留上面的 `event.remove(...)`、去掉 `event.custom(...)` 即可，然后执行 `/reload`。

删除、替换锁并 `/reload` 不会改写团队永久研究存档，不会清空已买节点、复制他队进度或自动退款；它改变的是今后制作时如何检查许可。原有构筑井研究等独立门槛也不会被这一步移除。

## 4. 制作时到底用谁的研究

这里的「本队」指 FTB Teams 团队；未安装 FTB Teams 时以玩家 UUID 作为个人归属。接网借用只授予临时制作许可，不会把出借团队的研究写入本队永久进度、替本队购买研究，或满足本队研究的永久前置。

| 制作场景 | 许可来源 |
| --- | --- |
| 普通背包 2×2、工作台 3×3，以及原版切石/锻造操作 | 操作者当前团队的永久研究。旁边有电缆、星算引擎或刚用过 ME 终端都不算借权。 |
| 不走 ME 借权的自动机器 | **机器放置时绑定团队**的永久研究，不是最后打开界面、投料或取出产物的玩家团队。 |
| ME 机器 | 本机绑定团队的永久研究，加上通过本机**真实且 active 的 ME 节点**取得的同网授权。离线节点不能出借权限。 |
| ME 工作台终端 | 操作者本队永久研究，加上**当前真正打开的终端操作节点**（terminal action node）所在在线网络的授权；不能拿附近网络或历史连接代替。 |
| 没有自身 ME 节点、但已适配的外部原版/模组机器 | 除自身绑定团队的永久研究外，只能通过**相邻、在线、输出方向朝向该机器的样板供应器**（Pattern Provider，含贴面部件）借用其网络授权。 |

网络授权取**同一个真实 ME 网络内，所有在线且已成型星算引擎所提供研究的并集**，不只取等级最高的一个。多个彼此隔离的网络不会拼成一个授权集合。

外部机器的借权不追踪物流的「原料从哪里来」：隔着管道的供应器、远程供应器、仅相邻的 ME 电缆/能源块，以及某次历史投料都不能代替上述邻接与朝向条件。**有合法供应器相邻，也只解决借权来源，不会自动给任意第三方机器增加执行适配。**

### 自动机器的归属与首次绑定

没有原生归属字段的自动机器由通用归属组件 `RecipeMachineOwnership` 在玩家放置时持久绑定；组件只保存团队身份，不保存借来的研究。

- 旧存档机器，或投掷器等非玩家方式放置且尚未绑定的机器，可由玩家近距离主手空手、按住 Shift 右键首次绑定到当前团队。服务器还检查建造/交互权限及非旁观者条件。
- 这项操作不能改变已有机器的归属。打开界面、换投料者、取出产物或玩家换队都不会自动改绑。
- 无主机器没有一份可共用的「匿名团队永久进度」；未命中锁的配方仍默认开放，临时借权也只能走上述真实网络途径。
- 原生物质构筑井与星算核心沿用自己的所有权和迁移规则，不被通用首次绑定覆盖。

机器归属用于判定制作许可，机器原有的交互、安全和领地权限仍须独立检查。与 claim/protection 模组的兼容需逐项确认。

### 断网后，正在做的那一单怎么办

断网、网络分裂、失去频道、供应器离线/转向，或出借引擎被移除/不再成型，都会在**下一次新作业准入**时重新检查。仅靠已消失网络借来的许可不再有效；本队永久研究或其他仍有效的授权不受牵连。

- 借权撤销后，已成功接受的本模组批次，以及 AE/EAE 装配室和矩阵线程已获准的单次作业仍按原有流程结算，不会仅因此被追溯取消。供电、结构、输出空间等正常工作条件仍然适用。
- 许可只覆盖已接受的这次作业；CPU 订单中的后续作业和已安装样板的下一次执行仍须检查。已有成品与应返余料不应因许可撤销而丢失。
- 熔炉、压印器、EAE 共享执行器等各有检查点；尚未消耗原料的工作可能暂停、重新查配方或重置进度。这些适配不提供所有机器统一的全量事务，也不保证所有在途工作都会继续完成。

## 5. 当前实际覆盖的制作入口

下表列出已接入检查的执行路径；只有命中锁的配方才受限。

| 入口 / 机器 | 当前覆盖内容与边界 |
| --- | --- |
| 背包与工作台 | 结果计算、普通取出、Shift 批量取出、缓存结果复核，以及配方书自动填料前检查。2×2 和 3×3 均覆盖。 |
| 熔炉族 | 熔炉、高炉、烟熏炉的查配方、缓存配方和燃烧/消耗前检查，按机器归属及有效借权判断。 |
| 篝火 | 放食物前和烹饪时检查；玩家放入时**玩家与篝火两边都须有许可**。被拒绝的槽位不会当作「无配方，丢出原料并清空」来处理。 |
| 原版合成器 Crafter | Minecraft 1.20.1 没有此 1.21 方块，本分支没有对应执行入口；不能据此推断其他模组的自动合成器已适配。 |
| 切石机、锻造台 | 按玩家检查选中配方、结果取出及 Shift 路径；切石保留原列表索引，不以删列表方式锁定。铁砧不是有稳定配方 ID 的锻造配方，不在此项承诺内。 |
| 本模组物质构筑井 | 新批次在原有井内研究之外叠加全局锁；已接单批次按上节边界结算。 |
| 本模组构序执行器 | 构序阵列、分子构序重写阵列、装配矩阵构序重写核心及构序自动合成器，在接收/准备批次、从 ME 提取原料前检查；已缓存的配方和可复用计划也不能代替许可。 |
| AE2 ME 工作台终端 | 预览刷新与实际制作均检查，包括 Shift / craft-all 和直接制作快捷路径；借权来自当前真实终端节点。 |
| AE2 分子装配室 | 接受样板作业与开始执行前检查，许可按一次已填料作业使用，不按缓存样板永久放行。 |
| AE2 压印器 | 初次查配方和每次返回缓存任务均检查，`/reload` 后重查；含下节的动态命名任务。 |
| AE2 样板供应器 | 派发带可信配方 ID 的合成样板时提前检查；不通过隐藏可用样板或阻止编码来实现。处理样板仍须目标机检查。 |
| ExtendedAE 扩展装配室 / 装配矩阵线程 | 覆盖 `CraftingThread` 与矩阵的缓存产物执行分支；接单和单次执行准入、已接单结算规则同上。 |
| ExtendedAE 扩展压印器 | 按各 `InscriberThread` 独立检查任务与缓存，重载后重新查找，不假定它继承 AE2 原压印器。 |
| ExtendedAE 切割 / 晶体机器 | Forge **1.4.20** 的电路切片器共享执行器 `RecipeExecutor.execute`、执行期间的 `RecipeSearchContext` 搜索/缓存，以及晶体修复器轮询入口。1.21 的水晶装配器不是本 Forge 基准机器；相似 API 不代表其他设备自动受支持。 |
| AdvancedAE，可选且限版本 | `RecipeLockAdvanced*` 钩子针对 Forge **`1.3.6-1.20.1`** 的反应室和量子合成器执行路径，由精确版本门控启用；不自动覆盖其他版本或 AdvancedAE 全部设备。 |

目标 API 为 AE2 **15.4.10**、ExtendedAE **1.20-1.4.20-forge**（ID `expatternprovider`）、Glodium **1.20-1.5-forge**。源分支内部接口的兼容结论不能直接用于 Forge。

### 处理样板、动态命名与导入配方

- **处理样板（ProcessingPattern）没有可信的 recipeID。** 输入/输出物品不足以唯一还原配方，供应器不能凭它锁死任意处理链。必须由实际目标机的适配器（adapter）在查找/执行配方时检查；未适配的第三方机器不在保证范围内。
- **NamePress 动态命名任务**虽然没有普通注册配方，现已标识为稳定虚拟 ID **`ae2:inscriber/name_press`**。可在锁中用 `match: { recipe_ids: ['ae2:inscriber/name_press'] }` 单独选择，也可用 `match: { recipe_types: ['ae2:inscriber'] }` 与其他压印配方一起选择。它**默认开放**，不会因为是动态任务就默认被拒绝；只有命中自定义锁才需要研究。
- **外部配方导入构筑井**时保留真实来源 ID（`sourceRecipeId`），执行时同时检查内部配方 ID 与原始来源配方 ID，不能靠导入后换内部名字绕过原锁，也不从输出物品猜来源。这是导入代码保留的身份，不是让脚本手填一个 `sourceID` 字段；构筑井原有研究仍须满足。

实现入口：[配方锁与归属](../src/main/java/com/atir/molecularmanipulator/recipelock/)、[各执行适配 `RecipeLock*Mixin`](../src/main/java/com/atir/molecularmanipulator/mixin/)、[研究定义与命令](../src/main/java/com/atir/molecularmanipulator/starcomputation/)。

## 6. 兼容边界

覆盖仅限第 5 节列出的执行入口，不是能自动锁住一切机器的通用 Mixin。新模组、自行缓存产物的机器、没有可信配方身份的流程或依赖版本变更，都需要核对真实执行入口，必要时新增适配后再承诺覆盖。

可选 AdvancedAE 权限钩子仅适配 Forge **1.3.6-1.20.1**，其他版本须单独适配。JEI 提示与目标机器的执行检查分别适配，处理样板仍须由实际目标机检查。
