---
navigation:
  parent: omnisequence-index.md
  title: "微型黑洞与微型白洞"
  icon: molecularmanipulator:black_hole
  position: 1110
item_ids:
- molecularmanipulator:black_hole
- molecularmanipulator:white_hole
---

# 微型黑洞与微型白洞

在同一维度中，把周围物品与非玩家实体从微型黑洞传送到微型白洞出口。

<Row>
<ItemImage id="molecularmanipulator:black_hole" scale="6" />

<ItemImage id="molecularmanipulator:white_hole" scale="6" />
</Row>

## 制作微型黑洞与微型白洞

先在构筑井完成**三阶：视界奇点**。使用样板总成执行以下大数量配方，每份产出一个；首次解锁时均为 30 秒、4096 AE/t。

| 产物 | 唯一原料 |
| --- | --- |
| 微型黑洞 ×1 | AE2 奇点 ×100K（10 万） |
| 微型白洞 ×1 | AE2 物质球 ×1G（10 亿） |

<RecipeFor id="molecularmanipulator:black_hole" />

<RecipeFor id="molecularmanipulator:white_hole" />

## 建立入口与出口

1. 先放置**微型白洞**，为出口上方留出通行空间。
2. 在同一维度放置**微型黑洞**，附近掉落物和非玩家实体会逐渐被拉向中心。
3. 靠近微型黑洞中心后，实体传送到微型白洞上方，并向上轻微弹出。

| 规则 | 行为 |
| --- | --- |
| 玩家 | 不会被吸取或传送 |
| 微型白洞数量 | 每个维度最多一个；拆除现有微型白洞后才能放置另一个 |
| 多个微型黑洞 | 共用该维度的微型白洞出口 |
| 没有微型白洞 | 微型黑洞不开始吸取 |
| 微型白洞区块 | 放置后强制加载，拆除后释放 |

微型黑洞本身需要所在区块加载才会工作。传送保留实体本身，物品不会先变成采集产物或奇点序质。

## 用在天枢中

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

把微型黑洞作为物品放入天枢的微型黑洞槽，可提高**奇点序质**产量。这个槽最多容纳 64 个微型黑洞，具体用法见[物质复制](singularity_matter_duplication.md)。
