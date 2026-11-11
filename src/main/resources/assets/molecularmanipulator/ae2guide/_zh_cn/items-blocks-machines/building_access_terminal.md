---
navigation:
  parent: omnisequence-index.md
  title: "建筑访问终端"
  icon: molecularmanipulator:building_access_terminal
  position: 1005
item_ids:
- molecularmanipulator:building_access_terminal
---

# 建筑访问终端

这是一个**随身机器目录**。 先到现场绑定建筑，以后在同一维度内就能远程打开它原来的操作界面， 不必每次跑回控制器旁边。每个终端最多保存 **64 个目标**，同型号的多座机器也能分别加入。

<Row>
<ItemImage id="molecularmanipulator:building_access_terminal" scale="5" />

<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

**手持终端 → 绑定机器控制器 → 从列表打开机器界面。** 机器仍在原地工作，终端不会把建筑或库存搬进背包。

## 准备什么

- 制作一台建筑访问终端，配方在页末。
- 先到要绑定的控制器旁边。建筑需要有自己可用的操作界面；星算引擎必须成型。
- 远程访问时，玩家与建筑要在**同一维度**，目标区块要**已经加载**。终端不会替你加载区块。

## 一步步绑定和打开

1. 手持终端，**潜行右键控制器**。看到绑定成功提示后，建筑就加入这台终端的列表。
2. **手持终端右键空气**，打开建筑列表。用鼠标滚轮或拖动右侧滚动条浏览； 名称或位置显示不全时，悬停查看详情。
3. 点击要操作的建筑，打开它原来的界面。无需靠近控制器，也不受客户端渲染距离限制。
4. 想换一台机器，点击界面左上角的**返回建筑列表**。

**移除**只删除这一台终端中的记录， 不会拆机、清空库存或影响另一台终端的绑定。 操作期间让终端留在原背包格；移走终端会关闭当前访问。

## 哪些建筑能绑定

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_manipulator" />
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:star_computation_engine_core" />
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:compact_singularity_hub" />
</ItemGrid>

- 分子构序重写阵列、构序阵列、构序自动合成器。
- 物质构筑井；其四类物品／流体服务接口与样板总成也可单独绑定。
- 星算引擎／天体仪：可点成型后的中央外壳、观测口或有效星位锚，绑定对应主核。
- 多方块万物演算核心；多方块与单方块奇点天枢。

普通装饰块、没有独立界面的结构件，以及超限算枢不能作为独立目标加入。

## 怎样算成功

列表中能看到建筑名称、维度和坐标； 选择可访问的建筑后，出现与现场右键相同的机器界面。 槽内物品和按钮操作都作用于**原机器**，不会生成另一份库存。

机器原本需要的 ME、能源、研究、成型和玩家权限仍然有效，终端只省去往返路程。

## 打不开时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 列表为空 | 先手持终端潜行右键支持的控制器，不是普通右键。 |
| 建筑不可访问 | 检查是否同维度，目标区块是否已加载，以及建筑和操作权限是否正常。 |
| 刚打开就关闭 | 不要移动终端；检查目标是否被拆除或区块卸载。 |
| 搬迁后旧记录打不开 | 到新位置重新绑定，并移除旧记录。 |
| 已有 64 条记录 | 先移除不再需要的目标，再绑定新建筑。 |

## 合成配方

<RecipeFor id="molecularmanipulator:building_access_terminal" />
