---
navigation:
  parent: omnisequence-index.md
  title: "物质构筑井"
  icon: molecularmanipulator:matter_fabrication_controller
  position: 900
item_ids:
- molecularmanipulator:matter_fabrication_controller
- molecularmanipulator:matter_fabrication_casing
- molecularmanipulator:matter_fabrication_glass
- molecularmanipulator:matter_fabrication_coil
- molecularmanipulator:matter_fabrication_stabilizer
- molecularmanipulator:matter_fabrication_core
---

# 物质构筑井

物质构筑井是一座用于材料加工和研究的多方块机器。它可以通过输入输出口加工原料，也可以安装样板总成，为 ME 网络执行自动合成。

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## 搭建结构

构筑井占地 **41 × 41**，高 **27 格**。控制器、基础结构件和输入输出口无需研究即可制作。完整材料清单可在 JEI 结构页查看。

1. 放置控制器，打开**投影**，检查建筑范围内的缺块和冲突。
2. 按清单准备材料，清理冲突后点击**一键搭建**。搭建先使用玩家背包，再使用控制器连接的 ME 库存。
3. 在服务位置安装所需的[输入输出口](matter_fabrication_ports.md)。手持接口可以查看这些位置。
4. 接通 ME 网络与电源，在控制器中确认结构成型和联网状态。

服务位置共 **44 格**：前方台阶 24 格，中央平台四段各 5 格。[样板总成](matter_fabrication_pattern_assembly.md)也安装在服务位置。

## 使用构筑井

| 工作方式 | 操作 |
| --- | --- |
| 材料加工 | 将物品与流体送入对应输入口，从输出口取走产物 |
| [研究](matter_fabrication_research.md) | 把研究材料存入 ME，在研究页选择分支并开始 |
| ME 自动合成 | 完成一阶研究，安装样板总成并放入对应处理样板 |

研究和加工可以同时进行。无法开始时，检查结构、供电、研究条件以及产物接收空间。

## 量子连接

在控制器量子槽放入一枚**缠绕态奇点**，将配对的另一枚放入已供电的 AE2 量子环，即可连接远端 ME 网络。量子连接额外需要 **512 AE/t 和 1 个频道**，也能在结构未成型时提供搭建材料。

## 拆除与搬迁

**一键拆除**保留控制器，将结构材料优先送回 ME，再放入背包。接收空间不足时暂停，腾出空间后可以继续。

正常拆下控制器会保留研究进度、已接收任务与量子槽内容；接口保留各自缓存，样板总成保留样板与加工材料。重新安装并恢复结构、网络和供电后可继续工作。

服务器启用多方块强加载时，成型与施工期间会保持所需区块加载。建筑占用区块内会阻止自然刷怪，刷怪笼、繁殖和已有生物不受影响。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<SubPages icons={true} />
