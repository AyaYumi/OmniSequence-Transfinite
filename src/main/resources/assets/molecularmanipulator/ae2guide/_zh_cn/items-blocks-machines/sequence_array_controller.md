---
navigation:
  parent: omnisequence-index.md
  title: "构序阵列控制器"
  icon: molecularmanipulator:molecular_center_controller
  position: 1030
item_ids:
- molecularmanipulator:molecular_center_controller
- molecularmanipulator:molecular_center_casing
- molecularmanipulator:molecular_center_glass
- molecularmanipulator:molecular_center_coil
- molecularmanipulator:molecular_center_stabilizer
- molecularmanipulator:molecular_center_core
---

# 构序阵列控制器

构序阵列是一座集中管理样板与自动补货的多方块机器。它提供大容量样板库，以及九个独立的自动合成位置。

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

## 结构与搭建

在构筑井完成**二阶：构序阵列**研究后制作部件。建筑占地 **61 × 61**，高 **29 格**，以中央控制器为基准，没有实体底座。

1. 在 JEI 结构页查看材料清单，放置控制器并开启**方块投影**。
2. 清理投影中的冲突，准备材料后点击**一键搭建**。搭建先使用背包，再使用 ME 库存。
3. 接通 ME 供电和频道，确认结构完整。

## 样板库

样板库默认 **200 页，每页 36 槽**，共 7200 槽；服务器配置可设至 300 页。成型后的样板由结构中的 **14 颗量子水晶**存放，可通过样板终端管理。

存储总线只访问主样板库。自动合成槽、量子槽和上传核心槽各有独立用途。

搭建、拆除或回收量子水晶期间，外部样板修改会暂停，完成后再继续整理样板。

## 控制器界面

| 页面 | 用途 |
| --- | --- |
| 装配总览 | 查看结构与联网状态，控制投影、搭建和拆除 |
| 自动合成 | 按成品目标与原料保留量自动补货 |
| 配色 | 设置建筑颜色 |

自动合成支持九张独立样板，具体操作见[分子自动合成器](molecular_auto_crafter.md)。安装 ExtendedAE Plus 时，其上传核心使用专用槽。

## 量子连接与回收

量子槽只接受**缠绕态奇点**。把配对的另一枚放入已供电的 AE2 量子环，可连接远端网络，也可在成型前获取施工材料。链路额外需要 **512 AE/t 和 1 个频道**。

**一键拆除**保留控制器，将材料先送 ME，再送背包；空间不足时暂停。正常拆下控制器会保留样板、任务和各专用槽内容。

服务器开启多方块强加载时，成型与施工期间保持所需区块加载。

## 配方

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
