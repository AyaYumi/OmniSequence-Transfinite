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

建筑访问终端能保存常用机器的位置，让你在同一维度内远程打开它们的操作界面。先到现场绑定，再从终端列表选择机器即可。每台终端最多保存 64 个目标，同型号的多座机器也能分别加入。

<Row>
<ItemImage id="molecularmanipulator:building_access_terminal" scale="5" />

<BlockImage id="molecularmanipulator:matter_fabrication_controller" scale="4" />

<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

机器仍在原地工作，终端里的槽位和按钮直接操作原机器。访问时仍需满足机器本身的 ME、能源、研究、成型和玩家权限要求。

## 准备什么

- 制作一台建筑访问终端，配方在页末。
- 先到要绑定的控制器旁边。建筑需要有自己可用的操作界面；星算引擎必须成型。
- 远程访问时，玩家与建筑要在同一维度，目标区块要已加载。终端不会加载区块。

## 一步步绑定和打开

1. 手持终端，潜行右键控制器。看到绑定成功提示后，建筑就加入这台终端的列表。
2. 手持终端右键空气，打开列表，查看建筑名称、维度和坐标。用鼠标滚轮或拖动右侧滚动条浏览，显示不全时可悬停查看详情。
3. 点击可访问的建筑，打开与现场相同的机器界面。无需靠近控制器，也不受客户端渲染距离限制。
4. 想换一台机器，点击界面左上角的“返回建筑列表”。

点击“移除”只会删除当前终端中的记录，原机器、库存和其他终端的绑定都会保留。操作期间让终端留在原背包格，移走终端会关闭当前访问。

## 哪些建筑能绑定

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_manipulator" />
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:star_computation_engine_core" />
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:compact_singularity_hub" />
</ItemGrid>

- 分子构序重写阵列、构序阵列、分子自动合成器。
- 物质构筑井；其四类物品／流体服务接口与样板总成也可单独绑定。
- 星算引擎／天体仪：可点成型后的中央外壳、观测口或有效星位锚，绑定对应主核。
- 多方块万物演算核心；多方块与单方块奇点天枢。

普通装饰块、没有独立界面的结构件，以及超限算枢不能作为独立目标加入。

## 无法访问时

| 现象 | 怎么处理 |
| --- | --- |
| 列表为空 | 先手持终端潜行右键支持的控制器，不是普通右键。 |
| 建筑不可访问 | 检查是否同维度，目标区块是否已加载，以及建筑和操作权限是否正常。 |
| 刚打开就关闭 | 不要移动终端；检查目标是否被拆除或区块卸载。 |
| 搬迁后旧记录打不开 | 到新位置重新绑定，并移除旧记录。 |
| 已有 64 条记录 | 先移除不再需要的目标，再绑定新建筑。 |

## 合成配方

<RecipeFor id="molecularmanipulator:building_access_terminal" />
