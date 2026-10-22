---
navigation:
  parent: omnisequence-index.md
  title: "负熵石英与晶种"
  icon: molecularmanipulator:negative_entropy_crystal
  position: 1210
item_ids:
- molecularmanipulator:negentropy_seed
- molecularmanipulator:negative_entropy_crystal
- molecularmanipulator:negative_entropy_crystal_dust
- molecularmanipulator:negative_entropy_alloy_ingot
- molecularmanipulator:negative_entropy_quartz_bud_small
- molecularmanipulator:negative_entropy_quartz_bud_medium
- molecularmanipulator:negative_entropy_quartz_bud_large
- molecularmanipulator:negative_entropy_quartz_cluster
- molecularmanipulator:negative_entropy_damaged_budding_quartz
- molecularmanipulator:negative_entropy_chipped_budding_quartz
- molecularmanipulator:negative_entropy_flawed_budding_quartz
- molecularmanipulator:negative_entropy_budding_quartz
---

# 负熵石英与晶种

负熵晶种可以把恩特罗化的福鲁伊克斯母岩一步步转成负熵母岩。负熵母岩会像赛特斯石英一样长芽，最终长出负熵石英簇。

<Row>
<ItemImage id="molecularmanipulator:negentropy_seed" scale="6" />

<ItemImage id="molecularmanipulator:negative_entropy_crystal" scale="6" />

<BlockImage id="molecularmanipulator:negative_entropy_quartz_cluster" scale="4" />
</Row>

## 晶种配方

在工作台无序合成：

- 充能赛特斯石英水晶 ×1
- 天空石粉 ×1
- 紫水晶碎片 ×1
- 雪球 ×1
- 末影珍珠粉 ×1

产出 **负熵晶种 ×2**。

<RecipeFor id="molecularmanipulator:negentropy_seed" fallbackText="当前整合包未提供可用配方，请查看 JEI。" />

## 转化母岩

1. 准备 ExtendedAE 的**恩特罗化福鲁伊克斯母岩**（完全、多半、半、几乎未恩特罗化）。
2. 手持负熵晶种右键母岩。每次消耗 1 个晶种（创造模式不消耗），母岩下降一级：
   - 完全 → 多半 → 半 → 几乎未恩特罗化
   - 几乎未恩特罗化 → **严重负熵石英母岩**
   - 严重 → **破损负熵石英母岩**
   - 破损 → **微瑕负熵石英母岩**
3. 晶种最多只能到**微瑕**，不会继续变成无瑕。

> 没有安装 ExtendedAE 时，晶种仍可修复已有的负熵母岩，但不能转化恩特罗母岩。

## 母岩等级

| 等级 | 说明 |
| --- | --- |
| 严重负熵石英母岩 | 最低级，长芽时不会继续掉级 |
| 破损负熵石英母岩 | 长芽时可能掉到严重 |
| 微瑕负熵石英母岩 | 长芽时可能掉到破损；晶种最高只能到这里 |
| 无瑕负熵石英母岩 | 永不掉级；当前版本没有生存获取途径 |

每次成功长芽，母岩有约 **1/12** 概率掉一级。严重不再下降，无瑕永不掉级。

## 生长与采集

母岩的六个面都可以长芽，顺序是：

**小负熵石英芽 → 中负熵石英芽 → 大负熵石英芽 → 负熵石英簇**

用镐采集成熟晶簇：

- 不带精准采集：掉落 **3 个负熵水晶**，受时运影响
- 带精准采集：掉落晶簇本身，可以搬回家继续长

## 材料

| 物品 | 用途 |
| --- | --- |
| 负熵水晶 | 晶簇的主要产物，负熵系列的基础材料 |
| 负熵水晶粉 | 当前版本为材料预留，暂无生存配方 |
| 负熵合金锭 | 当前版本为材料预留，暂无生存配方 |

当前版本**无瑕负熵石英母岩**没有生存获取途径，可在创造模式获取；后续会接入负熵引擎转化。
