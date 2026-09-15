---
navigation:
  title: "万象构序：超限"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 80
---

# 万象构序：超限

面向材料构筑、分子构序重写、大规模演算与高吞吐自动合成的 AE 系统。

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_pattern_assembly" />
<ItemIcon id="molecularmanipulator:matter_fabrication_item_input" />
<ItemIcon id="molecularmanipulator:matter_fabrication_fluid_input" />
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_manipulator" />
<ItemIcon id="molecularmanipulator:assembler_matrix_molecular_core" />
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:transfinite_compute_nexus" />
</ItemGrid>

## 推进路线

用表格把整条路线一次讲清，比连续段落更好读。

| 步骤 | 要做什么 |
| --- | --- |
| **1** | 搭建[物质构筑井](items-blocks-machines/matter_fabrication_well.md)。控制器、结构部件和输入输出口都用 AE2 原版材料制作，不需要研究。 |
| **2** | 完成[一阶研究](items-blocks-machines/matter_fabrication_research.md)，每轮 **30 秒**。它会开放 AE 材料加工、后续研究所需材料，以及[样板总成](items-blocks-machines/matter_fabrication_pattern_assembly.md)。 |
| **3** | 选择二阶分支。一阶只需完成**一次**，两个分支可以同时研究；深度研究提高该分支开放的构筑井配方能力。 |

## 两个分支

| 分支 | 多方块 | 同时开放 |
| --- | --- | --- |
| **构序阵列** | [构序阵列控制器](items-blocks-machines/sequence_array_controller.md) | [分子构序重写阵列](items-blocks-machines/molecular_sequence_rewrite_array.md)、[装配矩阵构序重写核心](items-blocks-machines/assembler_matrix_sequence_rewrite_core.md) |
| **万物演算** | [万物演算核心](items-blocks-machines/omni_computation_core.md) | [超限算枢](items-blocks-machines/transfinite_compute_nexus.md)——放下即可使用的单方块 CPU |

## 阅读方式

> 在物品栏或 JEI 中将鼠标悬停于对应机器或构筑井接口上，按指南快捷键（默认 `G`）可直接跳转到对应页面。

指南中的配方从当前世界读取，包括构筑井加工配方和研究解锁信息，整合包修改后会按实际显示。

<SubPages icons="true" />
