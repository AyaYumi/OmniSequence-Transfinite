# 数据包 / KubeJS 自定义星算研究节点

**整合包作者参考。** 本页用于给星算引擎的配方树添加团队永久研究，说明怎样定义、购买和保存。
玩家只需在机器中选择研究并点击解锁，不需要编写脚本；安装和正常进度见
[玩家指南](../README.zh-CN.md)，制作入口的许可规则见[配方权限](recipe-permissions.zh-CN.md)。

当前对应 OmniSequence **21.3.0.0**，Minecraft **1.21.1** / NeoForge、Java **21**。

星算研究是一份**解锁一次、由团队长期保留的制作许可**。打开本队星算引擎的**配方树**，选择研究，再点击解锁；仅选择条目不会购买。
引擎使用九环天体仪：中央 27 件与 928 个真实基座缺一不可，中央 3×3×3 不是独立机器。
它与每座井各自保存、用来开放加工工艺的井内研究是两套进度。

默认列表只有**单方块集成**（英文 **Single-Block Integration**，稳定 ID 仍为 `molecularmanipulator:material`）：要求核心实际生效等级 **L3**，费用 **0 bit**（`information_cost: 0`）、`prerequisites: []`。**免费不等于自动解锁**，仍须玩家主动点击，服务器确认后才写入本队永久进度。

唯一默认锁声明 `molecularmanipulator:recipe_locks/material` 只限制两条配方：单方块**分子构序重写阵列**（`molecularmanipulator:molecular_manipulator`）和单方块**超限算枢**（`molecularmanipulator:transfinite_compute_nexus`）。两者仍须满足原有井内工艺研究，材料、加工费用及其他条件不变；超限算枢仍仅在安装 AdvancedAE 时加载。物质构筑井、构序阵列和万物演算核心的多方块控制器均不受默认星算研究锁限制。

整合包可用 KubeJS 或数据包添加更多研究。执行 `/reload` 后，新条目会出现在**同一个配方树界面**，可直接选择和购买；总数超过五项时在列表中滚动查看，不必改用命令。

研究使用普通配方声明 `molecularmanipulator:star_research`，但不是制造配方，不在工作台中执行，也没有物品输出。

## 1. 用 KubeJS 添加研究与配方锁

把以下脚本保存为 `kubejs/server_scripts/star_research.js`：

```js
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:star_research',
    title: '自定义研究',
    required_level: 4,
    information_cost: 1024,
    prerequisites: ['molecularmanipulator:material']
  }).id('pack:custom')

  // 研究本身不会自动锁配方；这条规则让活塞需要上述研究。
  event.custom({
    type: 'molecularmanipulator:recipe_lock',
    requires: ['pack:custom'],
    mode: 'all',
    match: {
      recipe_ids: ['minecraft:piston']
    }
  }).id('pack:recipe_locks/custom')
})
```

保存后执行 **`/reload`**，确认日志没有加载错误。在本队已绑定、成型且 ME 在线的引擎中，先在核心实际等级达到 L3 时点击解锁「单方块集成」，再在实际 **L4**、拥有 **1024 星光情报**时选择「自定义研究」并解锁。它会像默认条目一样保存为本队永久进度；重复购买不再扣费。配方锁的组合方式与机器覆盖范围见[配方权限文档](recipe-permissions.zh-CN.md)。

**研究 ID 就是声明的配方 ID（RecipeHolder ID）**：上例由 `.id('pack:custom')` 指定，`prerequisites` 和锁的 `requires` 都引用它。标题不是 ID，也不要在对象里另加 `id` 字段。标题可直接写文本，也可写客户端语言文件中的翻译键。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `title` | 字符串，必填 | 显示标题；可直接写文本，也可写翻译键。空白标题回退显示节点 ID。 |
| `required_level` | 整数，必填，1..9 | 当前核心实际生效等级 `getBenefits().level()`；不是只看已搭建环数，也不借用同网其他核心等级。 |
| `information_cost` | 非负 64 位整数，必填 | 一次性消耗的星光情报；允许 0，最大为 `9223372036854775807`。小数、负数、越界数会被拒绝，而非截断。0 费用仍须主动解锁。 |
| `prerequisites` | 节点 ID 字符串数组，可省略 | 默认为空；列出的前置必须全部有当前有效定义，并已由**本队永久解锁**。 |

不用 KubeJS 时，可在数据包中创建 `data/pack/recipe/custom.json`（1.21.1 使用单数目录 `recipe`），得到同一个研究 ID `pack:custom`：

```json
{
  "type": "molecularmanipulator:star_research",
  "title": "自定义研究",
  "required_level": 4,
  "information_cost": 1024,
  "prerequisites": ["molecularmanipulator:material"]
}
```

配方锁需另存为普通 `recipe_lock` 声明。以上 JSON 与 KubeJS 研究示例二选一；同一 ID 是覆盖，不会生成两个节点。

KubeJS/JavaScript 的 Number 有精度限制；脚本中的整数建议不超过 `Number.MAX_SAFE_INTEGER`。非常大的 long 费用请使用数据包 JSON 的整数字面量；不要将费用写成字符串。

重新加载后，新定义及覆盖在命令目录、已打开菜单的下一次服务器同步和下一次购买时生效。研究总数没有固定的 5 / 32 项限制；自定义进度按完整 ID 保存，不借用旧的五项存档位。

## 2. 唯一内置研究、覆盖与动态列表

没有数据包声明时，目录只提供一个内置保底定义（fallback）：

| 研究 | 节点 ID | 实际等级 | 星光情报 | 本队前置 |
| --- | --- | ---: | ---: | --- |
| 单方块集成 / Single-Block Integration | `molecularmanipulator:material` | L3 | 0 | 无（`[]`） |

原来的 `foundation`、`quantum`、`terminal`、`transfinite` **不再有默认定义，也不会占据菜单条目**。旧存档中这些 ID 和位标记仍保留、不清除、不重排；整合包可以用原完整 ID（命名空间均为 `molecularmanipulator`）重新声明，恢复显示并沿用本队已有进度。`material` 也沿用原永久 bit 1，不会因为现在排在列表第一项而改为 bit 0。

要覆盖「单方块集成」，必须使用**完全相同的配方 ID**。例如，以下独立脚本只改标题，保持默认等级、零费用和空前置；需要时可调整这些值：

```js
ServerEvents.recipes(event => {
  event.custom({
    type: 'molecularmanipulator:star_research',
    title: '整合包单方块集成',
    required_level: 3,
    information_cost: 0,
    prerequisites: []
  }).id('molecularmanipulator:material')
})
```

数据包的对应路径是 `data/molecularmanipulator/recipe/material.json`。不要误用 `molecularmanipulator:research/material`，后者是另一个新节点。**`event.remove(...)` 不能删除内置保底定义**：移除 `material` 的覆盖声明后，会恢复上表这一项默认规则，已解锁进度不变。若只想取消上述两条单方块配方的默认制作限制，应删除对应的 `recipe_lock`，而不是移除研究，见配方权限文档。

菜单显示服务器同步的**完整当前目录**，包含自定义条目、标题、实际等级、费用、前置、配置错误，以及每项研究和直接前置的本队永久拥有状态。超过五项的研究仍可在同一界面滚动、选择、查看条件并购买。当前没有定义但存档里仍保留的 ID 不会仅凭记录变回菜单条目。

服务器收到解锁请求后，按**节点 ID**重新读取当前目录、核心实际等级、余额及本队永久进度，最终由 `core.tryUnlockResearch(player, nodeId)` 校验和支付，不采信客户端显示的可购买标记。列表位置不是存档位，也不是购买身份；重载后定义或排序变化不会把请求改成另一研究。同步期间界面可能短暂滞后，以服务器结果为准。

## 3. 可选的普通玩家命令

也可用命令查看或购买同一目录中的研究；它不是自定义节点的唯一入口。无需 OP 权限，只能由玩家使用，不是管理性免费授予命令。

```mcfunction
/starresearch list
/starresearch unlock molecularmanipulator:material
/starresearch unlock pack:custom
```

- 站在**核心中心 8 格内**，优先看向核心；也可看向已成型中央壳体或观测口。
- 若未看向可识别核心/中央壳体，命令会在玩家实际所在维度已加载区块中寻找 8 格内最近的本队已绑定核心。不会为了命令加载区块。
- 看着其他团队或未绑定的核心会拒绝，不会替玩家自动绑定或修改归属。更换 FTB 团队不会自动改变旧核心绑定。
- 不提供坐标参数、团队参数、`unlock_all`、远程终端目标或越权管理员选项；看向远处锚环不作为命令远程购买入口。
- `list` 显示完整节点 ID、标题、实际等级要求、费用、前置、本队状态和配置错误。点击节点 ID 可把购买命令填入聊天输入框；`unlock` 的 ID 参数支持建议补全。
- 暂时移除数据包后，`list` 还会列出本队“已永久解锁但当前未定义”的记录。
- 已解锁节点重复购买不扣费；当前定义有效时，相应命令返回 0，新购买成功返回 1。未知节点或当前配置无效的节点返回命令错误，不会扣费。

无论通过 GUI 还是命令购买，服务器都会再次检查：玩家所属团队等于核心 `boundTeam`、交互/建造权限、不是旁观者、核心未被移除、也不处于建筑恢复过程、结构已成型、主 ME 节点 active、实际等级足够、前置定义有效且由本队永久拥有、该节点尚未解锁，并通过原有 `extractInformation(..., PlayerSource)` 支付声明的费用。提取不足或支付失败不会写入研究。0 费用不提取情报，但仍需通过其他条件并主动解锁；创造模式不会免除自定义节点的费用。

## 4. 前置错误与进度隔离

缺失前置、自引用、多个节点组成的环，以及依赖这些错误节点的下游节点，都会被标记为不可购买。服务器在重建目录时记录带节点 ID 的警告；`list` 也会显示原因。**即使存档里曾经买过一个前置，当前目录未定义它仍然是配置错误，不能把未定义前置当作满足。** 无效的内置覆盖不会悄悄退回 fallback 来允许购买。

团队进度完全独立。永久购买只读写核心绑定团队的存档集合；同一 ME 网络提供给其他团队的授权是临时借用，**不能充当其永久购买前置，也不会复制为另一团队的进度**。本文说明节点声明与购买；配方锁规则格式与制作检查见配方权限文档。

调整费用、等级或前置不会撤销已买节点，也不会重新收费或自动退款。配方锁如何处理当前目录中的节点由配方锁层负责，不能把永久记录误当成当前节点仍有定义。

## 5. 永久存档与旧数据迁移

各维度共用服务器主世界的 SavedData：

```text
<存档>/data/molecularmanipulator_star_computation_research.dat
```

`teams` 中的每个条目包含：

- `team`：绑定团队 UUID；无法解析的归属不会授予匿名进度。
- `unlock_mask`：仍写入旧五节点 mask，保留旧 API/数据兼容。
- `unlocked_nodes`：完整的节点 ID 字符串列表，包含自定义节点及暂时不存在的节点。

读取时合并 mask 与 ID 列表，也合并重复团队条目；合法但当前不存在的 ID 不会被删除。旧 mask 的 bit 0..4 依次映射到 `foundation / material / quantum / terminal / transfinite`（命名空间均为 `molecularmanipulator`）。这是存档兼容映射，**不代表五项仍有默认研究定义**；`material` 始终保留 bit 1。旧核心自身的 `entropy_unlocks` 会通过 `getUnlockMask()` 导入其绑定团队，再由 `getUnlockedResearchIds()` 返回完整 ID 集合，不重新收费；尚未确定 `boundTeam` 的旧核心不授予匿名研究，之后绑定解析成功才导入。

写入这五个旧 ID 会同步反映在旧 mask 中；写入旧 mask 会自动生成对应 ID。其他自定义 ID 不使用位移或 int mask，不会因为数量超过 32 而折返覆盖旧节点。暂时移除自定义声明只会让它退出当前目录，已买 ID 仍在存档；重新声明同一 ID 后沿用本队已有进度。销毁、卸载或打包核心不会删除团队永久记录。

兼容不代表可以无损降级：旧版本模组只认识 mask，可能在重写 SavedData 时丢弃新的 `unlocked_nodes` 字段。跨版本回退前请备份世界。

## 6. Java / 整合接口

```java
// 完整只读节点目录：一个 material fallback + 数据包 / KubeJS 定义；key 就是 RecipeHolder ID。
Map<ResourceLocation, StarResearchNodeRecipe> nodes = StarResearchNodes.nodes(level);
// 包含不可购买原因；不存在的 ID 也返回错误。
String error = StarResearchNodes.validationError(level, nodeId);

var data = StarComputationResearchData.get(serverLevel);
Set<ResourceLocation> ownProgress = data.getUnlockedNodes(team);
boolean owned = data.isUnlocked(team, nodeId);

// 权威服务器购买入口：仅新购买返回 true，重复/无效请求返回 false 且不再扣费。
boolean purchased = core.tryUnlockResearch(serverPlayer, nodeId);
Set<ResourceLocation> boundTeamProgress = core.getUnlockedResearchIds();
```

`addUnlock(team, id)` 和旧 `addUnlock(team, index)` / `mergeUnlockMask(team, mask)` 是**不收费的持久化操作**，仅供已经完成授权、支付或迁移的服务器代码调用，不应作为面向玩家的购买入口。null team 的读结果为空/false/0，写入返回 false。返回的集合是不可修改的快照。

注册整合点（由主注册模块负责）：

- `ModContent.STAR_RESEARCH_RECIPE_TYPE`：`RecipeType<StarResearchNodeRecipe>`，ID `molecularmanipulator:star_research`。
- `ModContent.STAR_RESEARCH_RECIPE_SERIALIZER`：`StarResearchNodeRecipe.Serializer`，ID 同上。
- 命令由 `StarResearchCommands` 的 game-bus `@EventBusSubscriber` 自动注册，无需在模组主类手动重复挂接。

菜单的 `nodes()` / `node(index)` 提供当前同步列表，`indexOf(nodeId)` 按稳定 ID 查找临时列表位置。`@GuiSync(28) researchNodes` 使用 `StarResearchMenuNodes`（AE2 `PacketWritable`）同步完整动态列表和本队永久拥有状态，不再只传五项；旧同步编号 0..27 保留。静态 `NODES`、`RecipeNode` 和旧索引接口仅作兼容，不代表当前菜单数量，不能把列表索引当成旧永久位。

目录缓存随 `RecipeManager` 配方集合和 `PatternRecipeReloads.generation()` 更新失效；团队永久进度独立保存在 SavedData 中。KubeJS 在服务器重载时生成声明，客户端只使用服务器同步的定义，不执行购买脚本。
