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

把大容量样板库与持续补货合成集中在一座「霜晶羽冠」建筑中。

## 建成阵列

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

| 准备项 | 要求 |
| --- | --- |
| 解锁 | 构筑井完成二阶：构序阵列 |
| 空间 | 61 × 61，高 29 格 |
| 结构 | 无实体底座，中央控制器 |
| 样板库 | 默认 200 页 × 36 槽；配置最多 300 页 |

1. 按 JEI 材料清单准备部件，放置控制器并开启**方块投影**。
2. 清理红框冲突，点击**一键搭建**；优先背包取材，再从 ME 抽取。
3. 接通供电和频道，检测完整结构后使用样板与自动合成页面。

## 界面中的三个页面

| 页面 | 用途 |
| --- | --- |
| 装配总览 | 检查结构、投影、搭建、拆除与状态 |
| 自动合成 | 九个独立样板，按成品目标和原料保留量补货 |
| 配色 | 调整建筑的显示颜色 |

自动合成的详细设置见[分子自动合成器](molecular_auto_crafter.md)。

## 大容量样板库

<Row>
<BlockImage id="molecularmanipulator:molecular_center_core" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />

<ItemImage id="ae2:storage_bus" scale="4" />
</Row>

成型后样板库由 **14 颗量子水晶**分摊，可经样板终端管理。存储总线只访问主编码样板库；专用自动合成槽、量子槽与上传核心槽不对存储总线开放。

搭建、拆除与水晶回收期间，外部样板修改会暂停。拆除完成后，再按提示继续整理样板。

## 远程连接与回收

专用量子槽只接受**缠绕态奇点**。配对的另一枚放入已供电的 AE2 量子环，即可连接远端网络，未成型时也能取用施工材料；链路额外需要 **512 AE/t、1 个频道**。

可选的 ExtendedAE Plus 上传核心使用自己的专用槽位。构序矩阵成型、通电并接入编码终端所在 ME 网络后，编码的工作台、切石和锻造样板会自动进入主样板库。重复编码会返还空白样板；处理样板仍保留终端原有行为。兼容旧版 EAEP 和 1.6.x 上传接口。

**一键拆除**从高处回收，先送 ME，再送背包；没有空间时暂停。

正常拆下控制器时，样板、任务与各专用槽内容保存。启用多方块强加载配置后，完整结构与施工期间保持必要区块加载。

## 控制器配方

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
