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

材料加工、研究与 AE 自动合成的起点。先建成构筑井，再通过研究开放后续设备。

## 施工准备

<ItemGrid>
<ItemIcon id="molecularmanipulator:matter_fabrication_controller" />
<ItemIcon id="molecularmanipulator:matter_fabrication_casing" />
<ItemIcon id="molecularmanipulator:matter_fabrication_glass" />
<ItemIcon id="molecularmanipulator:matter_fabrication_coil" />
<ItemIcon id="molecularmanipulator:matter_fabrication_stabilizer" />
<ItemIcon id="molecularmanipulator:matter_fabrication_core" />
</ItemGrid>

| 准备项 | 要求 |
| --- | --- |
| 空间 | 41 × 41，高 27 格 |
| 初始阶段 | 0 阶，控制器与基础接口无需研究 |
| 材料清单 | JEI 结构页与控制器投影 |

## 从控制器到完整建筑

1. 放置控制器，正面朝向操作侧，打开**投影**检查缺块和冲突。
2. 按 JEI 清单备齐材料，清理冲突后点击**一键搭建**。
3. 施工优先取玩家背包，再取控制器连接的 ME 网络。
4. 在高亮的服务位置安装[输入输出口](matter_fabrication_ports.md)，成型后接通 ME 电源。

服务位置共 **44 格**：前方台阶 24 格，中央平台四段各 5 格。手持接口可查看安装位置。

## 选择工作方式

<Row>
<BlockImage id="molecularmanipulator:matter_fabrication_item_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_fluid_input" scale="4" />

<BlockImage id="molecularmanipulator:matter_fabrication_pattern_assembly" scale="4" />
</Row>

| 工作 | 如何开始 |
| --- | --- |
| [材料加工](matter_fabrication_ports.md) | 经物品、流体输入口投料，输出口接收产物 |
| [研究](matter_fabrication_research.md) | 在研究页选择分支，材料放入控制器的 ME 网络 |
| [AE 自动合成](matter_fabrication_pattern_assembly.md) | 一阶解锁样板总成后，放入匹配加工配方的处理样板 |

研究和加工可以同时进行。配方的实际材料、耗时、功率和研究条件以 JEI 与下方配方图为准。

## 远程施工与回收

<ItemGrid>
<ItemIcon id="ae2:quantum_entangled_singularity" />
<ItemIcon id="ae2:quantum_ring" />
<ItemIcon id="ae2:quantum_link" />
</ItemGrid>

量子槽放入一枚**缠绕态奇点**，配对的另一枚放入已供电的 AE2 量子环。未成型时也可取用远端施工材料；链路额外需要 **512 AE/t、1 个频道**。

**一键拆除**从上到下回收并保留控制器。材料先回 ME，再进背包；空间不足时暂停，腾出空间后继续。

| 拆下的方块 | 随方块保存 |
| --- | --- |
| 控制器 | 研究进度、已接收任务和量子槽内容 |
| 输入输出口 | 各自物品或流体缓存 |
| 样板总成 | 样板、材料与加工批次 |

重新安装后，恢复完整结构、网络与供电即可继续。启用多方块强加载配置时，成型和施工期间会保持所需区块加载；占用区块受到自然生成保护，刷怪笼、繁殖与已有生物不受影响。

## 控制器配方

<RecipeFor id="molecularmanipulator:matter_fabrication_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

## 继续阅读

<SubPages icons={true} />
