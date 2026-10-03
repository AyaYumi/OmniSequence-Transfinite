---
navigation:
  parent: items-blocks-machines/matter_fabrication_well.md
  title: "物质构筑井样板总成"
  icon: molecularmanipulator:matter_fabrication_pattern_assembly
  position: 2
item_ids:
- molecularmanipulator:matter_fabrication_pattern_assembly
---

# 物质构筑井样板总成

把 ME 自动合成请求交给构筑井。完成一阶研究后制作总成，并安装到合法服务位置。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

## 一张样板到一批产物

1. 在 JEI 中选择已解锁的构筑井加工配方。
2. 编码**处理样板**：全部输入、输出及数量必须匹配配方。
3. 把样板放入总成，给完整构筑井接通在线 ME 网络。
4. 从 ME 终端请求产物，总成收料后自动排队加工并返回输出。

| 样板设置 | 说明 |
| --- | --- |
| 容量 | 36 个样板槽 |
| 适用样板 | 与构筑井配方匹配的处理样板 |
| 研究加成 | 使用控制器对应分支的权限、并行和速度 |
| 多个总成 | 可以命名，在样板终端中区分 |

## 三个页面

| 页面 | 查看内容 |
| --- | --- |
| 样板 | 当前提供给 AE 的加工配方 |
| 输入缓存 | 已接收并等待加工的材料 |
| 输出缓存 | 等待 ME 网络接收的产物 |

缓存支持物品、流体和已注册的其他 AE 资源类型。相同资源及组件合并计数，每种资源使用长整数数量；实际加工仍受材料、功率与输出空间限制。

**退回待加工原料**只退还尚未开始的批次。已开始的任务继续保留，网络放不下的产物留在输出缓存。

## 搬迁后继续

样板、任务、材料、输出与待退回内容都会保存，正常拆下总成时随方块携带。重新安装并恢复结构、网络与供电即可继续。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
