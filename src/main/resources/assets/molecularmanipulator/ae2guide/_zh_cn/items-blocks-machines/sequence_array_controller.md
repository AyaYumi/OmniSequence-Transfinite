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
- molecularmanipulator:sequence_wireless_hub
---

# 构序阵列控制器

构序阵列默认可存 **7200 张样板**，并能在内部执行配方。样板需要在样板编码终端制作。另有 **9 个独立补货槽**，可在没有终端订单时自动维持库存。

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

阵列接受合成、锻造和切石样板。主库让终端可以请求这些配方，阵列负责实际制作，原料和成品存入 ME。

| 想做什么 | 样板放哪里 |
| --- | --- |
| 从终端下单，指定这次要多少 | 主样板库；网络还需**合成 CPU**，也就是接收和安排订单的设备。 |
| 常用成品少了就自动补满 | “自动合成”页的九个补货槽；由阵列自行取 ME 现有原料制作。 |
| 让熔炉等外部机器加工 | 将处理样板放到样板供应器，连接对应机器，不放进阵列。 |

## 准备

- 在[构筑井](matter_fabrication_research.md)完成一轮**Ⅲ阶：构序工程**，制作控制器和结构部件。这条路线使用 ExtendedAE 材料。
- 留出 **61 × 61 格、高 29 格**的空间。控制器在水平中央，相对控制器向下留 **6 格**、向上留 **22 格**。选址前先用投影检查，不需要实体底座。
- 准备 ME 供电、物品存储和空闲**频道**（网络给联网设备的连接名额）。终端下单还要准备空闲合成 CPU。
- 在 JEI 的结构页查看完整材料清单；下面这些是阵列自身的结构部件，不是另一套物品存储。

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="在 JEI 中查看控制器的构筑井配方与结构材料清单。" />

## 搭建并试用样板

1. 放置控制器，打开界面，在**装配总控**页开启**方块投影**。确认建筑不会穿进山体或已有机器。
2. 按结构清单准备材料，接入施工可用的 ME 网络，或把材料带在背包里。点击**一键搭建**；施工先使用背包，再使用 ME 库存。
3. 等待施工结束，检查界面同时显示**结构完整**和**在线**。补齐缺件、供电和频道后再放样板。
4. 在主样板库放入一张“橡木原木 → 橡木木板”的合成样板，向 ME 存入至少 **4 个原木**。
5. 从同网络终端请求 **16 个木板**，用空闲合成 CPU 确认订单。阵列会在内部制作，成品送回 ME。

主样板库默认 **200 页，每页 36 槽**；服务器可调整页数，最多 300 页。可在控制器翻页、搜索，也可用同网络的样板管理终端整理。样板存于结构中的 **14 颗量子水晶**，施工或回收水晶期间先等操作完成，再整理样板。

### 自动上传新样板

在总控页开启**自动上传**，之后在同网络的原生 AE2 样板编码终端编码合成、锻造或切石样板，新样板会直接进入主库。**新阵列默认关闭此功能**；重复配方会返还空白样板，满库或阵列未就绪时，编码样板留在终端。处理样板不上传。

已有样板可用“一键迁移样板”从同网络可取出的样板槽和在线存储盘搬入主库，只在点击时执行。处理样板留在原处；重复的实体样板分别保留，满库后未搬入的也留在原处。

### 自动补货

切到“自动合成”页，将样板放进九个专用槽之一并选中。填写成品目标和原料保留量，点击“设定”后启动。先用“木板目标 64、原木保留 8”试运行，**成品目标 0 代表不限量**。完整操作见[构序自动合成器](molecular_auto_crafter.md)。阵列的补货产物送回 ME。

## 成功标志

- 控制器显示结构完整且在线，样板终端能找到主库里的样板。
- 16 个木板的测试订单完成，成品出现在 ME，而不是占用样板槽。
- 开启补货后，目标未满时制作，达到目标时暂停，取走成品后继续。

## 排错

| 看到的情况 | 怎么处理 |
| --- | --- |
| 搭建停住或结构不完整 | 看提示补材料、清理投影冲突，并保持施工区域区块加载；所需材料可放背包或施工网络。 |
| 结构完整但离线 | 检查 ME 电力和频道；基础待机耗电默认为 **128 AE/t**。 |
| 放了样板却不能从终端下单 | 检查是否放在主库而不是补货槽，再检查同网络的空闲合成 CPU。 |
| 编码后的样板没有自动入库 | 开启自动上传，确认使用同网络原生 AE2 编码终端、主库有空位，且阵列已就绪。 |
| 不能用存储总线整理补货槽 | 存储总线只访问主样板库；补货槽和量子槽请在控制器中操作。 |
| 有订单却不出货 | 看合成状态中的缺料，并为 ME 成品和返还物腾出空间。 |

## 构序无线枢纽

用电缆把构序无线枢纽接到主 ME 网络，就能连接多台远端机器。连接可以跨维度，也没有距离限制。

<BlockImage id="molecularmanipulator:sequence_wireless_hub" scale="4" />

在工作台中央放 **1 个 AE2 量子链接仓**，周围放 **8 个量子环**，合成一个枢纽。建造天体仪之前就能做，无需解锁研究。

<RecipeFor id="molecularmanipulator:sequence_wireless_hub" fallbackText="工作台中央放 1 个 AE2 量子链接仓，周围放 8 个量子环；天体仪之前即可制作，无需研究解锁。" />

枢纽支持物质构筑井、构序阵列、分子构序重写阵列、构序自动合成器、星算引擎、多方块万物演算核心、视界奇点天枢和单方块奇点天枢。超限算枢要接电缆或其他外部 ME 设备。构筑井的端口和样板总成共用井控制器的绑定。各台机器仍需满足自己的搭建、研究和频道要求。

1. 移除机器外接的 ME 电缆。有量子槽时，取出槽里的奇点。
2. 在机器分页或自动合成配置页的右侧栏点击**远程连接**。
3. 选中枢纽，核对名称、维度、坐标和在线状态，再点**连接所选枢纽**完成绑定。

安装 FTB Teams 时，列表只显示和机器归属同一队伍的已加载枢纽。以放置机器时的队伍为准，该队成员和管理员可以管理绑定。未安装 FTB Teams 时，枢纽和机器须归同一玩家。机器原有的操作权限仍然生效。

面板顶部显示当前绑定和连接状态。点击“断开”可解除绑定。列表每页显示 3 个枢纽，可用按钮或滚轮翻页。用 Tab 或方向键选中控件，按 Enter 执行，按 Esc 关闭面板。

在侧栏绑定不需要卡片，也不占机器槽位。也可以手持 AE2 记忆卡潜行右键枢纽，再右键机器。要解除绑定，用已经选中枢纽的卡潜行右键机器。操作后可收起记忆卡，归属和权限规则照常生效。

按 AE2 默认设置，枢纽最多可传输 **32 个频道**。一台机器可能占用多个频道。枢纽待机耗电 **64 AE/t**，每条已建立的无线连接另加 **512 AE/t**，还要供给机器本身的耗电。用原有方式保持两端区块加载，也可使用机器自带的区块加载功能。

保存世界、正常拆下并恢复机器或整机打包都会保留绑定。如果拆掉、移动或替换枢纽本身，要重新选中它并逐台绑定。空手右键枢纽可查看状态、连接数和用法。枢纽的发光等级为 **0**。

## 下一步

断开无线枢纽后，也可用一对缠绕态奇点远程接网。将一枚放进控制器的量子槽，另一枚放进已供电的 AE2 量子桥的量子链接仓。这条连接额外需要 **512 AE/t 和 1 个频道**，成型前也能给施工供料。

搬家前先结清外部合成订单。点击“回收包裹”，在 **3 秒内**再次点击确认。包裹保留样板库、机内内容和无线绑定，放置方法见[折叠建筑与整机搬迁](folded_buildings.md)。

要接更多合成订单，见[万物演算核心](omni_computation_core.md)。需要小型补货设备时，可用[构序自动合成器](molecular_auto_crafter.md)。
