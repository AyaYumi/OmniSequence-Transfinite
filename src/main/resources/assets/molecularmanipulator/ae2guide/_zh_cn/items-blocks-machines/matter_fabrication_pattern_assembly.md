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

样板总成为[物质构筑井](matter_fabrication_well.md)提供 ME 自动合成功能。每个总成有 **36 个样板槽**，使用所属构筑井的研究解锁、并行和速度加成。

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />

<ItemImage id="ae2:pattern_encoding_terminal" scale="4" />

<ItemImage id="ae2:pattern_access_terminal" scale="4" />
</Row>

## 安装与编码

1. 完成一阶研究，制作样板总成并安装在构筑井服务位置。
2. 在 JEI 中选择已解锁的构筑井配方，将全部消耗材料和产物按对应数量编码为**处理样板**。
3. 把样板放入总成，确认构筑井成型并连接在线 ME 网络。
4. 从 ME 终端请求产物。总成接收材料后加工，将产物返回网络。

多个总成可以通过样板终端管理，命名后更容易区分。总成支持物品、流体及配方要求的其他 AE 资源。

## 从 JEI 上传样板

从**样板编码终端**打开构筑井的 JEI 配方，点击**编码并上传至构筑井**。此操作使用终端中的一张空白样板，按配方数量编码并上传到同队已成型构筑井的总成。优先选择同维度最近且有空位的总成。

目标必须处于已加载区块。没有可用总成、总成已满、样板重复或缺少空白样板时，不会消耗空白样板。

构筑井在放置时绑定放置者的 FTB 队伍；没有 FTB Teams 时绑定个人。换队后需要重新放置控制器来更新归属。上传目标无法找到时，检查队伍、结构和区块加载状态。

## 催化剂

需要催化剂的配方，应将催化剂放入总成的**催化剂**页。每个总成提供 **36 个催化剂槽**，同一座构筑井内已链接、已加载的总成共享这些物品。

催化剂不消耗，也不写入处理样板。例如，水晶催化配方只编码每轮消耗的水、闪电等材料；水晶块放入催化剂页。合成引力水晶所需的幽灵物质也按此方式使用。增加合成数量不会增加催化剂需求。

催化剂不足时，新任务等待；已经接收的任务保留材料与进度，放回催化剂后继续。编码时如果把催化剂列为消耗材料，请重新编码。

## 缓存与任务

| 页面 | 内容 |
| --- | --- |
| 样板 | 向 ME 网络提供的处理样板 |
| 输入缓存 | 已接收、等待加工的材料 |
| 输出缓存 | 等待 ME 接收的产物 |
| 催化剂 | 供同井配方使用的可复用物品 |

Neo ECO、数据能源三位一体和闪电科技 CPU 可以向总成提交批量加工。实际加工速度由研究、材料、供电和输出空间决定。

**退回待加工原料**只退回尚未开始的批次。已开始的任务继续保留；网络存储不足时，产物留在输出缓存，腾出空间后返回。

正常拆下总成会保留样板、催化剂、任务和缓存。重新安装并恢复结构、网络与供电后可继续。

## 配方

<RecipeFor id="molecularmanipulator:matter_fabrication_pattern_assembly" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
