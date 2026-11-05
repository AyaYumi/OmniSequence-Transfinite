---
navigation:
  parent: omnisequence-index.md
  title: "折叠建筑与整机搬迁"
  icon: molecularmanipulator:folded_building_package
  position: 890
item_ids:
- molecularmanipulator:folded_building_package
- molecularmanipulator:folding_assembler
- molecularmanipulator:star_chart_calibrator
---

# 折叠建筑与整机搬迁

用 ME 订购一整个建筑包裹，再用星图定标器选址展开。已经装好内容的机器也能整机封存，换位置或在同一存档的其他维度恢复。

<ItemGrid>
<ItemIcon id="molecularmanipulator:folding_assembler" />
<ItemIcon id="molecularmanipulator:folded_building_package" />
<ItemIcon id="molecularmanipulator:star_chart_calibrator" />
</ItemGrid>

## ME 下单

1. 制作**空间折叠装配器**，接入有电、可存储物品并能执行自动合成的 ME 网络。
2. ME 自动出现四种完整建筑包裹的合成入口：**物质构筑井、构序阵列、万物演算核心、普通星算引擎**。
3. 订购需要的建筑。每单按该建筑的标准结构消耗**控制器和全部建材**，完成后向 ME 交付**一个完整包裹**。

装配器使用普通 AE2 材料制作，第一座构筑井建成前即可使用。组成建筑的材料仍按原配方和研究条件获取。包裹无需填充，不需要另交控制器，也无需返还空壳。

新包裹提供标准结构。构筑井的可选样板总成和服务接口按需要安装；后续整机封存会带走实际安装的可选部件。

## 星图定标与展开

将包裹放在背包中，手持**星图定标器右击场地**。切换背包中的包裹，调整 X/Y/Z 和水平朝向；按住 Shift 时每次移动 8 格。**基面 Y 是建筑最低实体方块的高度**，建筑尺寸固定。

世界中的浅蓝线框标出真实占用位置，金色外框标出整体边界，红色标出冲突。结构要求留空的位置同样参与冲突检查。清理障碍后才能确认。

确认后依次出现**星图浮现 → 地基展开 → 立面拼接 → 核心落位**。恢复期间建筑暂停运行，完成后可像手工建造的结构一样编辑。包裹在建筑及内容保存成功后消耗，定标器可重复使用。取消选址保留包裹。

封存包裹的**封存清单**按页列出保存了原生内容的部件、相对位置和数据量；完整内容留在服务器存档中。

## 整机封存

1. 完成或取消所在 ME 网络中的外部 CPU 合成任务，并等待材料、产物与退款结算。停止建筑施工与拆除。
2. 手持定标器，**潜行右击控制器**，确认整机封存。
3. 领取一个包裹，在新位置按同样流程定标展开。背包已满时，腾出一格后在空中使用定标器领取。

封存保存控制器、实际结构件、已安装接口和样板总成，以及原生方块实体中的样板、催化剂、物品、流体、名称、设置、研究、已投入材料、内部加工进度、待输出与退款。阵列先保存控制器以同步 14 颗晶体中的样板，再收集晶体。旋转展开也会旋转接口输出面，重建控制器连接和 ME 节点。

## 存档与恢复

封存包裹可以放入 ME，也可以跨重启和同一存档内的维度使用。包裹只带档案编号与简要信息，完整建筑内容保存在世界目录的 `data/molecularmanipulator/buildings/` 中。**备份与搬迁存档时保留整个世界目录**。

展开开始后，包裹作为交接凭据留在原背包位置。服务器中断后会继续未完成的封存或展开；已经恢复的档案无法再次展开。档案不可用时保留包裹和已有档案数据，界面显示不可用状态。

把物品复制到另一个存档不会带上建筑档案；跨存档导出需要独立的导出流程。

## 配方

<RecipeFor id="molecularmanipulator:folding_assembler" />
<RecipeFor id="molecularmanipulator:star_chart_calibrator" />
