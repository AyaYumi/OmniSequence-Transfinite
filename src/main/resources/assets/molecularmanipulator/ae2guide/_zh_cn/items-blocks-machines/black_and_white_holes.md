---
navigation:
  parent: omnisequence-index.md
  title: "微型黑洞与微型白洞"
  icon: molecularmanipulator:black_hole
  position: 1110
item_ids:
- molecularmanipulator:black_hole
- molecularmanipulator:white_hole
- molecularmanipulator:miniature_supernova
---

# 微型黑洞与微型白洞

**黑洞是入口，白洞是出口**：把附近的掉落物和生物送到同维度、同队伍最近的白洞，不需要 ME 线缆。玩家不会被吸走，也不能用它传送自己。

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />

<ItemImage id="molecularmanipulator:miniature_supernova" scale="6" />
</Row>

从左到右：**微型黑洞（入口）→ 微型白洞（出口）**；微型超新星是制作天枢部件的材料，不是传送出口。

## 准备什么

- 在构筑井完成**Ⅳ阶：视界物质工程**，解锁黑洞与白洞；微型超新星另需**Ⅴ阶：恒星核心工程**。
- 用构筑井的[样板总成](matter_fabrication_pattern_assembly.md)加工下表配方。基础耗时均为 **20 秒、4096 AE/t**；星算引擎可缩短耗时。
- 给出口留出空间，并把不想运走的家畜与黑洞隔开：黑洞也会吸走附近生物。

| 制作 1 个 | 所需材料 |
| --- | --- |
| 微型黑洞 | AE2 奇点 ×10,000 |
| 微型白洞 | 微型黑洞 ×10 |
| 微型超新星 | 微型黑洞 ×100、微型白洞 ×100 |

天枢主控与白洞资源核心各需要一枚微型超新星。黑洞也可作为物品放进天枢生产序质，见[天枢物质复制](singularity_matter_duplication.md)。

## 一步步建立传送

1. **先放白洞。** 在它上方准备安全的落点、围栏或物品收集处。
2. **再放黑洞。** 两者必须在同一维度，由同一 FTB 队伍的成员放置；没有 FTB Teams 时，要由同一名玩家放置。
3. **先丢一个不重要的物品测试。** 物品被拉向黑洞，进入中心后会出现在最近的匹配白洞上方。
4. 测试成功后，再用这条路线运送物品或生物。 多个黑洞各自寻找最近出口；可以放置多个白洞。

## 怎样算成功

入口的掉落物消失，出口上方出现**同一件物品或同一只生物**，而不是生成副本。没有匹配白洞时，黑洞不会开始吸取。

白洞会持续加载自己所在的区块，拆掉后停止；黑洞所在区块仍需保持加载，才能持续工作。

## 不工作时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 黑洞完全不吸取 | 检查黑白洞是否同维度、同队，并确认黑洞所在区块已加载。 |
| 换队后找不到出口 | 队伍归属在放置时确定，重新放置黑洞和白洞。 |
| 送到了另一个白洞 | 黑洞自动选择最近的同队白洞，调整出口位置。 |
| 出口的生物四处跑 | 给白洞上方留空，并用围栏或其他设施接住生物。 |

## 配方查询

<RecipeFor id="molecularmanipulator:black_hole" fallbackText="在 JEI 中查看构筑井配方与研究要求。" />

<RecipeFor id="molecularmanipulator:white_hole" fallbackText="在 JEI 中查看构筑井配方与研究要求。" />

<RecipeFor id="molecularmanipulator:miniature_supernova" fallbackText="在 JEI 中查看构筑井配方与研究要求。" />
