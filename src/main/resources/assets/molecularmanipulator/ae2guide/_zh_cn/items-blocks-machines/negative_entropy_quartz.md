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
- molecularmanipulator:printed_negentropy_information_processor
- molecularmanipulator:negentropy_information_processor
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
- 带精准采集：掉落晶簇本身，便于搬运或装饰；持续产出水晶仍需母岩长芽

## 材料

| 物品 | 用途 |
| --- | --- |
| 负熵水晶 | 晶簇的主要产物，负熵系列的基础材料 |
| 负熵水晶粉 | 压印器将负熵水晶磨成粉，用于合金化 |
| 负熵合金锭 | AdvancedAE 反应仓合金化加工，作为引擎与熵锚的结构材料 |
| 负熵信息电路板 | 压印器配合运算处理器压印模板加工负熵水晶 |
| 负熵信息处理器 | 电路板、红石与硅板在压印器组装，用于引擎和熵锚 |

### 水晶粉与合金锭

不放模板时，压印器将 **1 个负熵水晶加工成 1 个水晶粉**。在 **AdvancedAE 反应仓** 中投入水晶粉 ×4、铁锭 ×4、福鲁伊克斯粉 ×1、天空石粉 ×1 和 **500 mB 水**，总能耗 **51,200 AE**，产出 **4 个负熵合金锭**。反应仓加工不需要构筑井研究；未安装 AdvancedAE 时，此合金配方不会加载。

<RecipeFor id="molecularmanipulator:negative_entropy_crystal_dust" />

<RecipeFor id="molecularmanipulator:negative_entropy_alloy_ingot" />

### 电路板与处理器

将 **运算处理器压印模板** 放在压印器上槽、负熵水晶放在中槽，产出负熵信息电路板；模板不消耗。

再把电路板放在上槽、红石放在中槽、硅板放在下槽，组装成负熵信息处理器。两步配方都不消耗负熵信息流。

<RecipeFor id="molecularmanipulator:printed_negentropy_information_processor" />

<RecipeFor id="molecularmanipulator:negentropy_information_processor" />

当前版本**无瑕负熵石英母岩**没有生存获取途径，可在创造模式获取；后续会接入负熵引擎转化。
