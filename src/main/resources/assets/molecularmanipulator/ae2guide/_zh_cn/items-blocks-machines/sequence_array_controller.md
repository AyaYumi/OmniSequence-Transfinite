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

存储总线只访问主样板库。自动合成槽和量子槽各有独立用途。

搭建、拆除或回收量子水晶期间，外部样板修改会暂停，完成后再继续整理样板。

## 控制器界面

| 页面 | 用途 |
| --- | --- |
| 装配总览 | 查看结构与联网状态，控制投影、搭建、整机回收、自动上传与样板迁移 |
| 自动合成 | 按成品目标与原料保留量自动补货 |
| 配色 | 设置建筑颜色与本机特效档位 |

自动合成支持九张独立样板，具体操作见[分子自动合成器](molecular_auto_crafter.md)。

在总览开启**自动上传**后，同一网络原生 AE2 终端中新编码的合成、切石和锻造样板会自动存入阵列。新阵列默认关闭；重复配方退回空白样板，阵列未就绪或样板库满时，编码物品留在终端。处理样板不上传。

点击**一键迁移样板**，将同网络公开样板槽和在线存储盘中的合成、切石、锻造样板移入主库。迁移仅在点击时执行，不会持续收集；重复实体和物品全部组件原样保留。私有库存、只读配方列表、存储总线代理和处理样板不动。满库时剩余样板留在来源，完成后报告迁移数量或部分完成状态。

旧上传核心槽已移除。仍可解码的旧核心会通过控制器输出缓存返还；尚未保存开关时保留原先开启行为，玩家已保存的开关值优先。

配色页的**特效**按钮可选 0 关闭、1 精简、2 完整，档位随本机保存并同步观看者，不影响产量。Shift+点击恢复沿用各客户端全局设置。

## 量子连接与回收

量子槽只接受**缠绕态奇点**。把配对的另一枚放入已供电的 AE2 量子环，可连接远端网络，也可在成型前获取施工材料。链路额外需要 **512 AE/t 和 1 个频道**。

**回收包裹**将控制器、已安装结构、样板库与内部内容整机封存为轻量包裹。首次点击后在三秒内再次确认；请先结清外部 ME 合成任务。完整数据保存在当前世界档案中，详见[折叠建筑与整机搬迁](folded_buildings.md)。正常拆下控制器仍会保留样板、任务和各专用槽内容。

服务器开启多方块强加载时，成型与施工期间保持所需区块加载。

## 配方

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
