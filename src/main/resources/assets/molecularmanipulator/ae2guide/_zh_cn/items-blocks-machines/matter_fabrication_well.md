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

物质构筑井能把原料加工成高级材料； 研究可以解锁更多配方，装上样板总成后，还能直接接受 ME 网络的自动合成订单。

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## 先准备什么

- 留出**占地 41 × 41、高 27 格**的空间。它是一整座建筑，不是只放一个控制器就能工作。
- 按下方配方制作控制器。控制器、基础建材和输入输出口都不需要研究。
- 在 JEI 的完整构筑井结构页查看全部建材清单。
- 准备有电、有存储的 ME 网络，给控制器留出一个频道。控制器待机消耗 **16 AE/t**，加工和研究另需供电。

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" />

## 一步步搭好

1. **放下控制器。** 打开界面，点击**投影**查看建筑的位置；先清走标为冲突的方块。
2. **提供建材。** 把建材放在背包，或存入控制器连接的 ME 网络，点击**一键搭建**。施工先用背包，再用 ME 库存。
3. **等待施工完成。** 重新打开控制器，用**检测**确认是否还有缺块或冲突。
4. **安装接口。** 手持[输入输出口](matter_fabrication_ports.md)，找到高亮位置，把那里原有的外壳换成接口。按第一条加工配方准备对应输入口和输出口。
5. **确认联网。** 控制器显示在线后，才能研究和加工。

可安装接口或样板总成的位置共 **44 格**：前方台阶 24 格，中央平台四周各 5 格。这些设备需要**替换指定位置的外壳**；摆在建筑旁边不算接入。

## 做出第一件产物

1. 按[研究指南](matter_fabrication_research.md)完成**Ⅰ阶：AE 基础构筑**。**研究只从 ME 库存取料，不读取背包或输入口。**
2. 做一次简单加工：向物品输入口放入**样板供应器 ×4、空白样板 ×4、合成单元 ×1**，并给物品输出口留出空间。
3. 构筑井会自动把这些材料加工成 **1 个样板总成**，不需要另按加工启动按钮。
4. 装上总成，就能开始 [ME 自动合成](matter_fabrication_pattern_assembly.md)；也可以继续用接口加工物品和流体配方。

供电足够时，研究和材料加工可以同时进行。

## 成功时看哪里

控制器的结构数量已经齐全，网络显示在线。加工时状态变为**正在构筑物质**，进度向前推进。试做的样板总成出现在物品输出口； 以后由样板总成接到的订单，则把产物送回 ME。

## 不工作时先查这些

| 现象 | 处理方法 |
| --- | --- |
| 施工停住 | 查看缺料提示，清理投影冲突，并保持整个建筑范围的区块已加载。 |
| 结构不完整 | 用**投影**和**检测**定位；确认接口替换的是高亮外壳位置。 |
| 网络离线或能量不足 | 检查控制器的线缆、频道和 ME 供电；接上线不代表电量足够。 |
| 没有可用加工配方 | 检查研究条件和每种原料数量；研究材料放 ME，加工原料放输入口。 |
| 输出堵塞 | 清空对应物品、流体输出口；样板订单则需给 ME 存储腾出空间。 |

## 常用控制

- **远程取建材：**控制器放一枚缠绕态奇点，配对的另一枚放入已供电的 AE2 量子环。连接额外需要 **512 AE/t** 和一个频道；构筑井尚未建好时，也能用它取得建材。
- **特效：**选择关闭、精简或完整特效，不改变产量。Shift+点击恢复沿用各观看者的全局设置。
- **回收包裹：**先点击一次，再于三秒内确认，将建筑和内部内容整机打包。先完成或取消外部 ME 合成任务，详见[折叠建筑与整机搬迁](folded_buildings.md)。

正常拆除会保留控制器的研究和已接收任务、接口库存， 以及总成的样板和材料。恢复建筑、网络和供电后即可继续。

## 接下来做什么

- [构筑井研究](matter_fabrication_research.md)：解锁第一批配方。
- [输入输出口](matter_fabrication_ports.md)：投料、取走产物。
- [样板总成](matter_fabrication_pattern_assembly.md)：从 ME 终端下单生产。
- [星髓晶体](star_marrow_crystals.md)：建立水晶和星髓材料供应线。
