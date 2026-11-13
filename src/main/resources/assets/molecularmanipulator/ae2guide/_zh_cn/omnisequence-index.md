---
navigation:
  title: "万象构序：超限"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 80
---

# 万象构序：超限

物质构筑井用来研究和加工材料。接好第一座井、完成首轮研究后，装上样板总成就能从 ME 终端下单加工，再按需要添置批量制作和自动补货设备。

## 入门步骤

![先接通构筑井，再完成一阶研究，最后装样板总成让 ME 自动加工](assets/diagrams/first-steps-zh_cn.png)

1. 接好 ME 电源、电缆、存储和终端，把原料存进去。还没接触过 AE2？先看 [AE2 入门](ae2:getting-started.md)。
2. 制作物质构筑井控制器，预留 **41×41 格、27 格高**的空地。用投影检查场地，按材料表备料后搭建，确认成型和 ME 在线。[跟着搭第一座井](items-blocks-machines/matter_fabrication_well.md)。
3. 完成Ⅰ阶：AE 基础构筑。在研究页查看费用，将材料存入相连 ME，再开始研究。背包和输入口里的材料不算研究库存。[首次研究步骤](items-blocks-machines/matter_fabrication_research.md)。
4. 装上样板总成，为井配方编码处理样板并放入总成。ME 订单还需要原料、可用合成 CPU 和产物空间。[样板总成怎么用](items-blocks-machines/matter_fabrication_pattern_assembly.md)。

第一次完成首轮研究就能开始加工，后续轮次用来提高批量。默认的量子材料、演算和后续研究需要 AdvancedAE；打算继续这些路线时，先确认整合包已安装它。

## 样板、CPU 和存储

![终端提出订单，CPU 安排步骤，样板告诉制作机器配方，原料和成品存进 ME](assets/diagrams/me-crafting-zh_cn.png)

- ME 存储用来放原料和成品。
- 样板记录配方的输入和产出，在样板编码终端制作。不同机器接受的样板类型不同。
- 合成 CPU 接收订单并安排步骤，仍需备齐原料和实际加工的机器。
- 频道是 ME 设备的连接名额。机器接了电缆还显示离线时，也要检查频道。

摆放尺寸和接线方法见各设备页面。构序阵列能在同一座建筑中保存样板并执行配方。

## 按用途选择设备

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />

<BlockImage id="molecularmanipulator:omni_computation_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

从左到右是构筑井、构序阵列、演算核心和视界奇点天枢的控制器。它们各有用途，可以按需求搭建。

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
| 让支持的机器接入远端 ME | [构序无线枢纽](items-blocks-machines/sequence_array_controller.md) |
| 传送掉落物和非玩家生物 | [微型黑洞与白洞](items-blocks-machines/black_and_white_holes.md) |
| 一次部署整座建筑，或带着机器搬家 | [折叠建筑与整机搬迁](items-blocks-machines/folded_buildings.md)、[建筑访问终端](items-blocks-machines/building_access_terminal.md) |
| 沿墙行走、做升降通道，或探索个人模拟 | [引力水晶与幽灵物质](items-blocks-machines/nomai_materials.md)、[模拟灯笼](items-blocks-machines/simulation_lantern.md) |

## 两种研究

井内研究解锁该井的工艺，后续轮次提高一次加工的份数。联动机器研究要求两个Ⅲ阶分支各完成三轮。星讯诱导结晶和异态物质学都是可选支线。

星算研究提供团队许可。先把九环天体仪升至 L3，再到配方树点击解锁免费的“单方块集成”。默认只有重写阵列和超限算枢额外需要这项许可。天体仪需要完整的 3×3×3 中央结构和基座。

## 用这本指南查东西

把鼠标放在背包物品或 JEI 条目上，按指南键，默认为 G。相关方块也有指南入口。点击文中物品图标可查介绍和配方，拖动结构图可以旋转。按键无效时，到“选项 → 控制”检查指南键。

整合包可以调整费用和条件，以 JEI、研究页和机器状态显示的内容为准。卡住时先查对应页面的常见问题。

<SubPages icons={true} />
