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

物质构筑井把原料加工成高级材料。研究可以解锁更多配方，装上样板总成后，还能接受 ME 网络的自动合成订单。

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

## 先准备什么

- 为整座建筑留出 **41 × 41 格、高 27 格**的空间。
- 按下方配方制作控制器。控制器、基础建材和输入输出口都不需要研究。
- 在 JEI 的完整构筑井结构页查看全部建材清单。
- 准备有电、有存储的 ME 网络，给控制器留出一个频道。控制器待机消耗 **16 AE/t**，加工和研究另需供电。

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" />

## 一步步搭好

1. 放下控制器，打开界面，点击“投影”查看位置，清走标为冲突的方块。
2. 将建材放在背包或控制器连接的 ME 网络中，点击“一键搭建”。施工先用背包，再用 ME 库存。
3. 等施工完成，重新打开控制器，用“检测”查找缺块或冲突。
4. 手持[输入输出口](matter_fabrication_ports.md)查看高亮位置，将对应外壳换成接口。按第一条加工配方安装所需输入口和输出口。
5. 确认控制器显示在线，再开始研究和加工。

接口和样板总成共用 44 个安装位置：前方台阶 24 格，中央平台四周各 5 格。手持设备时会高亮这些位置，需要用设备替换指定外壳。

## 做出第一件产物

1. 按[研究指南](matter_fabrication_research.md)完成Ⅰ阶：AE 基础构筑。研究只从 ME 库存取料，所需材料要先存入 ME。
2. 向物品输入口放入样板供应器 ×4、空白样板 ×4、合成单元 ×1，并给物品输出口留出空间。
3. 构筑井会自动加工，状态显示“正在构筑物质”，进度开始推进。完成后，物品输出口里会出现 1 个样板总成，无需另按启动按钮。
4. 装上总成，就能开始 [ME 自动合成](matter_fabrication_pattern_assembly.md)，订单产物会送回 ME。也可以继续用接口加工物品和流体配方。

供电足够时，研究和材料加工可以同时进行。

## 建造或加工遇到问题

| 现象 | 处理方法 |
| --- | --- |
| 施工停住 | 查看缺料提示，清理投影冲突，并保持整个建筑范围的区块已加载。 |
| 结构不完整 | 用**投影**和**检测**定位；确认接口替换的是高亮外壳位置。 |
| 网络离线或能量不足 | 检查当前 ME 连接、频道和供电。无线接网时，也要确认枢纽在线且连接生效。 |
| 没有可用加工配方 | 检查研究条件和每种原料数量；研究材料放 ME，加工原料放输入口。 |
| 输出堵塞 | 清空对应物品、流体输出口；样板订单则需给 ME 存储腾出空间。 |

## 连接与常用控制

需要无线接入 ME 时，在右侧栏用“远程连接”绑定构序无线枢纽。从端口或样板总成打开面板，设置的也是所属井控制器的连接。连接条件见[无线枢纽设置](sequence_array_controller.md)。

也可在控制器放一枚缠绕态奇点，配对另一枚放入已供电的 AE2 量子环。这种连接额外需要 **512 AE/t** 和一个频道，未成型时也能取得施工建材。

特效可选关闭、精简或完整，不改变产量。Shift+点击恢复沿用各观看者的全局设置。

搬迁时先完成或取消外部 ME 合成任务，再点击“回收包裹”，于三秒内再次确认。包裹保留内部内容和无线绑定，详见[折叠建筑与整机搬迁](folded_buildings.md)。

正常拆除会保留研究、已接收任务、接口库存、总成样板和材料，以及控制器的无线绑定。恢复建筑、ME 连接和供电后即可继续。如果无线枢纽也被移动或替换，需要重新绑定构筑井。

## 接下来做什么

- [构筑井研究](matter_fabrication_research.md)：解锁第一批配方。
- [输入输出口](matter_fabrication_ports.md)：投料、取走产物。
- [样板总成](matter_fabrication_pattern_assembly.md)：从 ME 终端下单生产。
- [星髓晶体](star_marrow_crystals.md)：建立水晶和星髓材料供应线。
