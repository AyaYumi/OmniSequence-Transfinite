---
navigation:
  parent: index.md
  title: 谐振合成编码终端
  icon: ae2cs:resonant_template_coding_terminal_part
  position: 140
item_ids:
  - ae2cs:resonant_template_coding_terminal_part
  - ae2cs:wireless_resonant_terminal
---

# 谐振合成编码终端

<Row gap="16">
  <ItemImage id="ae2cs:resonant_template_coding_terminal_part" scale="2" />
  <ItemImage id="ae2cs:wireless_resonant_terminal" scale="2" />
</Row>

**谐振合成编码终端**是一种高级样板编码终端，可以编码普通 AE2 样板和 AECS 谐振样板，拥有有线 ME 终端部件与无线终端两种形态。

---

## 配方模式

终端提供以下专用模式：

- 工作台合成
- 处理配方
- 锻造配方
- 切石配方
- 铁砧配方

处理配方模式最多提供 **144 个输入槽**和 **36 个输出槽**，大型配方不再受普通样板编码终端槽位容量的限制。

---

## 处理样板选项

编码处理配方时，可以选择生成**普通处理样板**或**谐振样板**。谐振样板可以继续为每种材料设置独立投放目标，并交由 <ItemLink id="ae2cs:resonating_pattern_provider" /> 使用。

通过 JEI 或 EMI 转入配方时，可以选择三种材料排列方式：

- **合并材料：** 与普通 AE2 终端相同，合并相同材料。
- **部分拆分：** 保留配方顺序，只合并相邻的相同材料。
- **完全拆分：** 保留每一项转入材料，不合并重复材料。

输入区域还可以在虚拟编码槽和真实槽之间切换。使用真实槽时，转入配方会从 ME 网络中抽取已有材料。

---

## 无线使用

<ItemLink id="ae2cs:wireless_resonant_terminal" /> 可以在远离固定终端的位置使用相同的编码界面。它可以通过兼容的能量容器充电；安装 AE2WTLib 时，也可以装入无线通用终端。
