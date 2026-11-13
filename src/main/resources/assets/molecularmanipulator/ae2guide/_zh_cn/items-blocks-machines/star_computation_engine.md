---
navigation:
  parent: omnisequence-index.md
  title: "星算引擎"
  icon: molecularmanipulator:star_computation_engine_core
  position: 1200
item_ids:
- molecularmanipulator:star_computation_engine_core
- molecularmanipulator:star_computation_engine_casing
- molecularmanipulator:star_computation_observation_port
- molecularmanipulator:star_orrery_engraved_plinth
- molecularmanipulator:star_orrery_gilded_plinth
- molecularmanipulator:reference_star_position_anchor
- molecularmanipulator:quantum_star_position_anchor
- molecularmanipulator:terminal_star_position_anchor
---

# 星算引擎

九环天体仪能为同一 ME 网络上的设备加速：构筑井加工更快，构序设备一次制作更多份，合成 CPU 也能接更大的订单。先完成第一环，再按需要扩建。

<Row>
<BlockImage id="molecularmanipulator:star_computation_engine_core" scale="4" />

<BlockImage id="molecularmanipulator:reference_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:quantum_star_position_anchor" scale="4" />

<BlockImage id="molecularmanipulator:terminal_star_position_anchor" scale="4" />
</Row>

从左到右：中央主核、前三环使用的基准锚、第 4～6 环的量子锚、第 7～9 环的终末锚。

## 先准备什么

- 在构筑井完成**Ⅱ阶：星髓材料工程**，准备[星髓加工材料](star_marrow_crystals.md)。核心、外壳、观测端口和基准锚在工作台制作，量子锚与终末锚在井中加工。
- 用 JEI 的**天体仪结构**预览确认空间与完整材料表。左拖旋转、右拖平移、滚轮缩放，分层按钮能看清基座。
- 一套有电的 ME 网络和 **1 个空闲频道**。频道就是 ME 设备使用的连接名额；整座引擎只占一个，待机耗能 **16 AE/t**。

即使没有星环的 L0，也需要完整的中央 **27 件**和下方 **928 块真实基座**。

## 推荐起步：订购完整 L1 包裹

1. 将[空间折叠装配器](folded_buildings.md)接入 ME，从终端订购“天体仪 L1”。包裹消耗整座建筑的实际材料，包含中央、基座和带 24 个锚的第一环。
2. 把包裹带在背包里，手持**星图定标器**右键选址，选择包裹和朝向，检查投影后确认展开。所选基面是最低基座层，**核心在其上方 37 格**。
3. 将 ME 电缆接到已成型天体仪的中央外壳、观测端口、主核或有效星位锚。基座不能接线，散放的部件也需要依托已成型机器。无线接网可用右侧栏的“远程连接”，操作见[构序无线枢纽设置](sequence_array_controller.md)。
4. 右键有效部件打开**总览**，确认结构成型、ME 在线、有效等级 **L1**。这时同网构筑井默认获得 **2 倍速度**，不用先生产星讯流。

也可直接订购 L2 或 L3 完整包。它们分别带前两环、前三环，仍只用基准锚。包裹的完整材料如下：

| 包裹 | 主核 | 外壳 | 观测端口 | 雕纹基座 | 鎏金基座 | 基准锚 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| L1 | 1 | 22 | 4 | 520 | 408 | 24 |
| L2 | 1 | 22 | 4 | 520 | 408 | 56 |
| L3 | 1 | 22 | 4 | 520 | 408 | 96 |

## 手动搭建中央和基座

### 中央：每层九块，共三层

下图将三层并排展开：左边是底层，中间是中层，右边是顶层。实际搭建时上下贴合，组成 3×3×3 中央结构。

<GameScene zoom="3" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-core-layers.snbt" />
  <IsometricCamera yaw="180" pitch="65" />
</GameScene>

- 底层、顶层各铺满九个外壳。
- 中层：中心放主核，四个边的中央放观测端口，四角放外壳。
- 合计 **1 主核 + 22 外壳 + 4 观测端口**，没有留空位置。

### 基座：有空隙的四层环架

这是实际的 **37 格宽、四层高**基座，可拖动旋转观察。中央竖井和环间空隙要留空，普通金块不能代替鎏金基座。

<GameScene zoom="0.7" background="transparent" interactive={true}>
  <ImportStructure src="../assets/structures/orrery-foundation.snbt" />
  <IsometricCamera yaw="225" pitch="45" />
</GameScene>

需要 **520 个星仪雕纹基座 + 408 个星仪鎏金基座**。核心位于最低层上方 37 格；精确安装位置以 JEI 分层图为准。中央和基座齐全后自动成型为 **L0**，再补星环获得等级和增益。

成型后外观会变成悬浮环架，但原位置的方块仍有碰撞。发光星环会转动，**实际锚位不会跟着动画移动**，不要追着动画摆方块。

## 扩建：在升级页补一环

1. 打开**升级**，选择下一个等级，查看缺少的锚和可用数量；只查看不会花材料。
2. 按下表准备材料。正确位置已有的锚会计入，只消耗缺件；**先用背包，不足再从本机相连的 ME 取用**。
3. 点击**自动搭建 L… 星环**或**升级到 L…**。保持锚位没有障碍、区块已加载，按提示处理缺料或冲突。
4. 返回总览，确认有效等级上升。L4 以后必须逐级点击，不会自动连升。

| 新增环 | 本环使用 | 本环锚数 |
| --- | --- | ---: |
| 1 / 2 / 3 | 基准星位锚 | 24 / 32 / 40 |
| 4 / 5 / 6 | 量子星位锚 | 48 / 56 / 64 |
| 7 / 8 / 9 | 终末星位锚 | 72 / 80 / 88 |

前三环累计 **96 基准锚**，中三环累计 **168 量子锚**，后三环累计 **240 终末锚**。L1～L3 也可手搭后自动生效；L4～L9 要在升级页逐级确认。默认没有额外升级能量、研究或任务费用，正常待机和超频耗费另计。

等级以总览显示为准。内环缺件会使该环及外侧环失效，需要从内向外补齐。整合包增加的升级条件会列在升级页；有能量校准时，会显示已付进度和暂停、继续按钮。

## L3：免费解锁两种单方块设备

默认只有一项团队研究：**单方块集成**。

1. 使用本队的天体仪，保持结构完整、ME 在线，确认**实际有效等级达到 L3**。
2. 打开**配方树**，选中**单方块集成**。
3. 点击“解锁”使研究生效，费用为 **0 bit**，没有前置，也不需要星讯库存。

| 设备 | 仍须完成的构筑井研究 |
| --- | --- |
| [分子构序重写阵列](molecular_sequence_rewrite_array.md) | Ⅲ阶：构序工程 |
| [超限算枢](transfinite_compute_nexus.md) | Ⅵ阶：超限集成工程；配方还需安装 AdvancedAE |

默认只有这两条配方额外需要星算许可。构筑井、构序阵列和多方块演算核心控制器都不需要解锁这项研究。

解锁属于本队永久进度，拆引擎或重启不会清除。同一在线 ME 网络可临时借用其他队伍引擎的制作许可，离网后借用结束。借来的许可不会成为本队永久进度，也不能满足自定义研究的永久前置。整合包追加的研究同样显示在配方树中，列表较长时滚动查看条件。

## 加速有什么用？

- 构序设备并行表示一次能做多少份配方。
- CPU 并行表示演算核心一次能推进多少份合成步骤。
- CPU 合成存储是订单和中间材料的工作空间，与 ME 仓库容量分开计算。
- 构筑井速度缩短生产时间，研究时间不变。九轮深度研究另行增加批量。

下列数值适用于未超频状态，实时能力可在总览和升级预览查看。

| 等级 | 构序并行 | CPU 并行 | CPU 合成存储 | 井速度 |
| --- | ---: | ---: | ---: | ---: |
| L1 | 4,096 | 512 | 1 GiB | ×2 |
| L2 | 16,384 | 1,024 | 4 GiB | ×3 |
| L3 | 65,536 | 2,048 | 16 GiB | ×4 |
| L4 | 262,144 | 4,096 | 64 GiB | ×8 |
| L5 | 1,048,576 | 8,192 | 256 GiB | ×12 |
| L6 | 4,194,304 | 16,384 | 1 TiB | ×16 |
| L7 | 16,777,216 | 32,768 | 4 TiB | ×32 |
| L8 | 67,108,864 | 65,536 | 16 TiB | ×48 |
| L9 | 268,435,456 | 131,072 | 64 TiB | ×64 |

同一网络只选**一座最佳在线引擎**，多座不会叠加。供电、频道、结构或原料不足，仍会限制实际产量。L7 起演算设备可用虚拟 CPU 同时承接多个订单，详见[万物演算核心](omni_computation_core.md)。

### 可选：超频

先用[星海解算主楼](star_sea_calculation_mainframe.md)生产星讯流，装进[星讯存储元件](star_signal_flow.md)，再在总览打开超频。星讯流不是 AE 电力；常态加速不消耗它。

- L1～L9 每 tick 分别消耗 **2 / 4 / 8 / 16 / 32 / 64 / 128 / 256 / 512 bit**，正常速度每秒约 20 tick。
- L1～L8 超频把上述四项收益翻倍，**不提升等级，也不替代研究**。
- L9 只有在持续付费超频时，才提供无限并行、无限 CPU 合成存储和井 **1 tick** 生产；常态 L9 仍是表中的有限数值。
- 星讯不足会关闭超频，退回常态收益；断网或结构坏了则无增益。永久研究不会丢失。

## 不工作时先看这里

| 现象 | 先检查什么 |
| --- | --- |
| 只有小立方体，无法成型 | 还要完整四层基座；中央也必须是 27 件。 |
| 有外观，但没有加速 | 总览中的有效等级、供电、频道；机器是否接在同一 ME 网络。 |
| 锚很多，等级仍低 | 从第一环起逐环检查；L4 起还需在升级页确认。 |
| 点击升级没成功 | 材料数量、挡住锚位的方块、区块加载和放置权限。 |
| L3 但配方仍锁着 | 主动解锁单方块集成，并完成对应井内研究。 |
| 超频自己关了 | 检查星讯库存、生产速度和网络状态。 |

搬迁前先结清外部 ME 合成订单。在总览点击“回收包裹”，稍等后在三秒内再次确认，再用定标器在同一存档展开。包裹等级按回收时连续完整的实物环数确定，详情见[整机搬迁](folded_buildings.md)。正常恢复和打包会保留无线绑定；如果无线枢纽被移动或替换，需要重新绑定。

旧存档的小立方体也要补齐中央和真实基座，缺少的材料需自行准备。

## 制作配方

<RecipeFor id="molecularmanipulator:star_computation_engine_core" />

<RecipeFor id="molecularmanipulator:star_computation_engine_casing" />

<RecipeFor id="molecularmanipulator:star_computation_observation_port" />

<RecipeFor id="molecularmanipulator:star_orrery_engraved_plinth" />

<RecipeFor id="molecularmanipulator:star_orrery_gilded_plinth" />

<RecipeFor id="molecularmanipulator:reference_star_position_anchor" />

<RecipeFor id="molecularmanipulator:quantum_star_position_anchor" />

<RecipeFor id="molecularmanipulator:terminal_star_position_anchor" />
