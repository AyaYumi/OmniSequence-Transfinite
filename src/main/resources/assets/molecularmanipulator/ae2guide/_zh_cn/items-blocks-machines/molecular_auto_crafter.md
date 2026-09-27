---
navigation:
  parent: omnisequence-index.md
  title: 构序自动合成器
  icon: molecularmanipulator:molecular_auto_crafter
  position: 1015
item_ids:
- molecularmanipulator:molecular_auto_crafter
---

# 构序自动合成器

<BlockImage id="molecularmanipulator:molecular_auto_crafter" scale="8" />

构序自动合成器是构序阵列被动自动合成功能的单方块版本。它接入 ME 网络后，会按专用样板槽中的配置持续合成物品，并把产物和余料写回同一个网络。

## 使用方法

1. 放下方块并接入已供电、拥有频道的 ME 网络。
2. 在界面中放入最多 9 个编码的合成、锻造或切石样板。
3. 点击样板上方的齿轮（或右键样板）进入设置页，设置成品库存上限与各原料的保留量；点击“设定”、按 Enter，或点击“保存全部”提交。
4. 原料超过五种时，用设置页右下角的箭头翻页。点击“返回”后，通过样板下方的按钮启动或停止该槽位。
5. 成品上限设为 `0` 时持续生产，直到原料保护量或网络能量不再允许下一批。

每个样板独立调度；缺料、缺电、输出暂时阻塞或达到库存上限时会等待，并在条件恢复后自动重试。它不提供普通样板供应器的外部推送槽，也不包含构序阵列的物质重写和量子链路功能。

<RecipeFor id="molecularmanipulator:molecular_auto_crafter" fallbackText="研究或配方被禁用时无法制作此方块。" />
