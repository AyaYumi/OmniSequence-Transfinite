---
navigation:
  title: "万象构序：超限"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 80
---

# 万象构序：超限

从**一座加工井**开始，逐步建起能自动备料、批量生产、补齐库存的 AE2 基地。这本指南会先告诉你该做什么，再介绍设备细节；不用先记住所有机器名字。

## 第一次玩，从这里开始

![先接通构筑井，再完成一阶研究，最后装样板总成让 ME 自动加工](assets/diagrams/first-steps-zh_cn.png)

1. **先有一套能用的 ME 仓库。** 接好电源、电缆、存储和终端，把原料存进去。还没接触过 AE2？先看 [AE2 入门](ae2:getting-started.md)。
2. **搭建物质构筑井。** 制作控制器，预留 **41×41 格、27 格高**的空地。打开投影看缺块，按材料表备料后搭建，再确认成型和 ME 在线。[跟着搭第一座井](items-blocks-machines/matter_fabrication_well.md)。
3. **完成Ⅰ阶：AE 基础构筑。** 在井的研究页查看费用，将材料存入相连 ME，再开始研究。**背包和输入口里的材料不算研究库存。** [首次研究步骤](items-blocks-machines/matter_fabrication_research.md)。
4. **让井接 ME 订单。** 制作并装上样板总成，为井配方编码处理样板，放入总成。自动合成还要有原料、可用 CPU 和产物空间。[样板总成怎么用](items-blocks-machines/matter_fabrication_pattern_assembly.md)。

第一次只需完成首轮研究，不必先刷满九轮。AdvancedAE 不是启动必需项，但默认量子材料、演算及后续研究需要它； 想走完整路线，请先确认整合包已经安装。

## 分不清样板、CPU 和存储？

![终端提出订单，CPU 安排步骤，样板告诉制作机器配方，原料和成品存进 ME](assets/diagrams/me-crafting-zh_cn.png)

- **ME 存储**是仓库，存原料和成品。
- **样板**是一张配方卡，告诉网络“拿什么，做出什么”。在样板编码终端制作，不同机器接受的样板类型不同。
- **合成 CPU**负责接单和安排步骤，不会自己变出原料，也不能代替实际加工机器。
- **频道**是 ME 设备的连接名额。机器接了电缆还显示离线时，也要检查频道。

上图说明的是分工，不是摆放尺寸。 构序阵列还把样板库和实际制作合在一座机器里； 详细接线看各设备页面。

## 接下来，按你想解决的问题选设备

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

从左到右是加工井、构序阵列、演算核心、视界奇点天枢的控制器。 它们是不同用途的建筑，不是四个必须依次替换的版本。

| 我想…… | 应该看哪页 |
| --- | --- |
| 研究、加工新材料 | [构筑井](items-blocks-machines/matter_fabrication_well.md)、[研究路线](items-blocks-machines/matter_fabrication_research.md) |
| 用管道送物品和流体，或从 JEI 编码井配方 | [输入输出口](items-blocks-machines/matter_fabrication_ports.md)、[样板总成](items-blocks-machines/matter_fabrication_pattern_assembly.md) |
| 保存大量样板，并自动维持成品数量 | [构序阵列](items-blocks-machines/sequence_array_controller.md)、[分子自动合成器](items-blocks-machines/molecular_auto_crafter.md) |
| 用一格机器批量合成、锻造、切石 | [分子构序重写阵列](items-blocks-machines/molecular_sequence_rewrite_array.md) |
| 升级 ExtendedAE 装配矩阵的批量制作能力 | [装配矩阵构序重写核心](items-blocks-machines/assembler_matrix_sequence_rewrite_core.md) |
| 接更大的合成订单，后期同时接多单 | [万物演算核心](items-blocks-machines/omni_computation_core.md)、[超限算枢](items-blocks-machines/transfinite_compute_nexus.md) |
| 给同网设备加速 | [星算引擎：九环天体仪](items-blocks-machines/star_computation_engine.md) |
| 种水晶、生产和存储星讯流 | [星髓晶体](items-blocks-machines/star_marrow_crystals.md)、[星海解算主楼](items-blocks-machines/star_sea_calculation_mainframe.md)、[星讯存储](items-blocks-machines/star_signal_flow.md) |
| 自动产出原材料，或用样品复制物品 | [视界奇点天枢](items-blocks-machines/event_horizon_singularity_hub.md)、[资源采集](items-blocks-machines/singularity_resource_collection.md)、[物质复制](items-blocks-machines/singularity_matter_duplication.md) |
| 用创造模式的小机器体验天枢 | [单方块奇点天枢](items-blocks-machines/compact_singularity_hub.md) |
| 传送掉落物和非玩家生物 | [微型黑洞与白洞](items-blocks-machines/black_and_white_holes.md) |
| 一次部署整座建筑，或带着机器搬家 | [折叠建筑与整机搬迁](items-blocks-machines/folded_buildings.md)、[建筑访问终端](items-blocks-machines/building_access_terminal.md) |
| 沿墙行走、做升降通道，或探索个人模拟 | [引力水晶与幽灵物质](items-blocks-machines/nomai_materials.md)、[模拟灯笼](items-blocks-machines/simulation_lantern.md) |

## 两件容易搞混的事

**井内研究**解锁该井的工艺；后续研究轮次提高一次加工的份数。联动机器研究要求两个Ⅲ阶分支**各完成三轮**。星讯诱导结晶和异态物质学都是可选支线。

**星算研究**是另一套团队许可。先把完整九环天体仪升至 **L3**，再到配方树主动解锁免费的**单方块集成**，默认只额外限制重写阵列和超限算枢。天体仪不是独立的 3×3×3 小机器：中央和完整基座都不能少。

## 用这本指南查东西

把鼠标放在物品或 JEI 条目上，按指南键，默认 **G**；相关方块也有对应入口。 点击文中物品图标能继续查介绍和配方，结构图可拖动旋转。 按键无效时，到**选项 → 控制**检查指南键。

本书使用默认玩法；整合包可以调整费用和条件，实际以 JEI、研究页和机器状态为准。卡住时先读对应页面的“不工作时”表格。

<SubPages icons={true} />
