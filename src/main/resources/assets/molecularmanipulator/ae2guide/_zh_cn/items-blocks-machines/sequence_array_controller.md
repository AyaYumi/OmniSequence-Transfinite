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

构序阵列默认可保存 7200 张样板，并在内部执行合成、锻造和切石配方。样板在样板编码终端制作。另有 9 个独立补货槽，能在没有终端订单时自动维持库存。

<Row>
<BlockImage id="molecularmanipulator:molecular_center_controller" scale="4" />
<ItemImage id="ae2:crafting_pattern" scale="4" />
<ItemImage id="ae2:smithing_table_pattern" scale="4" />
<ItemImage id="ae2:stonecutting_pattern" scale="4" />
</Row>

把样板放进主库后，就能从终端请求制作。阵列从 ME 取原料，完成后将成品存回 ME。

| 想做什么 | 样板放哪里 |
| --- | --- |
| 从终端下单，指定这次要多少 | 主样板库；网络还需要接收和安排订单的合成 CPU。 |
| 常用成品少了就自动补满 | “自动合成”页的九个补货槽；由阵列自行取 ME 现有原料制作。 |
| 让熔炉等外部机器加工 | 将处理样板放到样板供应器，连接对应机器，不放进阵列。 |

## 准备

- 在[构筑井](matter_fabrication_research.md)完成一轮Ⅲ阶：构序工程，制作控制器和结构部件。这条路线使用 ExtendedAE 材料。
- 留出 **61 × 61 格、高 29 格**的空间。控制器在水平中央，向下留 6 格、向上留 22 格。选址时先用投影检查，不需要实体底座。
- 准备 ME 供电、物品存储和空闲频道。频道是 ME 设备使用的连接名额，终端下单还需要空闲合成 CPU。
- 在 JEI 的结构页查看完整材料清单，下方图标列出阵列自身的结构部件。

<ItemGrid>
<ItemIcon id="molecularmanipulator:molecular_center_controller" />
<ItemIcon id="molecularmanipulator:molecular_center_casing" />
<ItemIcon id="molecularmanipulator:molecular_center_glass" />
<ItemIcon id="molecularmanipulator:molecular_center_coil" />
<ItemIcon id="molecularmanipulator:molecular_center_stabilizer" />
<ItemIcon id="molecularmanipulator:molecular_center_core" />
</ItemGrid>

<RecipeFor id="molecularmanipulator:molecular_center_controller" fallbackText="在 JEI 中查看控制器的构筑井配方与结构材料清单。" />

## 搭建并制作第一批物品

1. 放置控制器，打开界面，在**装配总控**页开启**方块投影**。确认建筑不会穿进山体或已有机器。
2. 按结构清单准备材料，接入施工可用的 ME 网络，或把材料带在背包里。点击**一键搭建**；施工先使用背包，再使用 ME 库存。
3. 等待施工结束，检查界面同时显示**结构完整**和**在线**。补齐缺件、供电和频道后再放样板。
4. 在主样板库放入一张“橡木原木 → 橡木木板”的合成样板，向 ME 存入至少 **4 个原木**。
5. 从同网络终端请求 **16 个木板**，用空闲合成 CPU 确认订单。阵列会在内部制作，成品送回 ME。

主样板库默认 200 页，每页 36 槽；服务器可调整页数，最多 300 页。可在控制器翻页、搜索，也可用同网络的样板管理终端整理。样板保存在结构中的 14 颗量子水晶里，施工或回收水晶时，先等操作完成再整理样板。

### 自动上传新样板

在总控页开启“自动上传”，之后在同网络的原生 AE2 样板编码终端编码合成、锻造或切石样板，新样板会直接进入主库。新阵列默认关闭这项功能。重复配方会返还空白样板；满库或阵列未就绪时，编码样板留在终端。处理样板不会上传。

已有样板可用“一键迁移样板”从同网络可取出的样板槽和在线存储盘搬入主库，只在点击时执行。处理样板留在原处；重复的实体样板分别保留，满库后未搬入的也留在原处。

### 自动补货

切到“自动合成”页，将样板放进九个专用槽之一并选中。填写成品目标和原料保留量，点击“设定”后启动。例如设木板目标为 64、原木保留量为 8，库存不足时阵列就会补货，达到目标后暂停，取走成品后继续。**成品目标 0 代表不限量**。补货产物送回 ME，完整操作见[分子自动合成器](molecular_auto_crafter.md)。

## 常见问题

| 看到的情况 | 怎么处理 |
| --- | --- |
| 搭建停住或结构不完整 | 看提示补材料、清理投影冲突，并保持施工区域区块加载；所需材料可放背包或施工网络。 |
| 结构完整但离线 | 检查 ME 电力和频道；基础待机耗电默认为 **128 AE/t**。 |
| 放了样板却不能从终端下单 | 检查是否放在主库而不是补货槽，再检查同网络的空闲合成 CPU。 |
| 编码后的样板没有自动入库 | 开启自动上传，确认使用同网络原生 AE2 编码终端、主库有空位，且阵列已就绪。 |
| 不能用存储总线整理补货槽 | 存储总线只访问主样板库；补货槽和量子槽请在控制器中操作。 |
| 有订单却不出货 | 看合成状态中的缺料，并为 ME 成品和返还物腾出空间。 |

## 用构序无线枢纽接入远端 ME

构序无线枢纽用电缆接入主 ME 网络后，可为多台远端机器提供无线连接，支持跨维度且没有距离限制。在接收机器右侧栏打开连接面板，选好枢纽并确认即可，不需要卡片，也不占机器槽位。

<BlockImage id="molecularmanipulator:sequence_wireless_hub" scale="4" />

支持的机器包括物质构筑井、构序阵列、分子构序重写阵列、分子自动合成器、星算引擎、多方块万物演算核心、视界奇点天枢和单方块奇点天枢。超限算枢不支持。各分页和自动合成配置页都有连接入口；从构筑井端口或样板总成打开面板，设置的是所属井控制器的连接。建造、成型、研究和频道要求仍然生效。

安装 FTB Teams 时，列表只显示与接收机器放置归属队伍相同、且已加载的枢纽。该队成员和管理员可以管理连接。打开界面不会改变归属，跨队绑定不能联网。未安装 FTB Teams 时按玩家独立归属处理，机器本身的操作限制仍然生效。

1. 移除接收机器的外部 ME 电缆。有量子槽时，也要取出其中的奇点。这些连接方式不能同时使用。
2. 点击机器界面右侧栏的**远程连接**。
3. 选择同队已加载的枢纽，核对名称、维度、坐标和在线文字，再点击**连接所选枢纽**。只选中一行不会改变绑定。

面板上方显示当前绑定和实际连接状态，点击“断开”可解绑。每页显示 3 个枢纽，可用翻页按钮或滚轮浏览，也可用 Tab 和方向键选中控件、Enter 执行、Esc 关闭面板。连接是否生效，以在线文字为准；紫色动态缩略图只作装饰。

AE2 记忆卡是备用绑定工具。持卡潜行右键枢纽选中，再右键机器绑定；持已选中的卡潜行右键机器可解绑。绑定后不必继续携带卡片，也不能用它绕过队伍检查。

枢纽在天体仪之前即可制作，无需研究解锁。工作台中央放 1 个 AE2 量子链接仓，周围放 8 个量子环，得到 1 个枢纽。

<RecipeFor id="molecularmanipulator:sequence_wireless_hub" fallbackText="工作台中央放 1 个 AE2 量子链接仓，周围放 8 个量子环；天体仪之前即可制作，无需研究解锁。" />

枢纽默认可传输 32 个频道，带宽随 AE2 配置变化。每台机器内部也可能有多个占频道的节点，不能将它直接理解为 32 台机器的名额。

枢纽待机耗电 **64 AE/t**，每台已建立无线连接的机器另加 **512 AE/t**，还要供给机器本身的耗电。

枢纽和接收机器所在区块都要保持加载。无线连接不会强制加载区块，原有多方块区块加载设置仍然适用。

正常存档、拆除恢复和建筑打包都会保留机器的绑定。枢纽身份会跨世界重启保留，但拆掉、替换或移动枢纽会生成新身份，需要重新选择它并逐台绑定。

空手右键枢纽可查看状态、连接数和用法。信号纹路离线时为黑色，接线、供电且在线时变紫。方块发光等级为 **0**，不会照亮周围。

## 下一步

断开无线枢纽后，也可用一对缠绕态奇点远程接网：一枚放控制器量子槽，配对的另一枚放入已供电的 AE2 量子环。这种连接额外需要 **512 AE/t 和 1 个频道**，也可在成型前为施工取料。

搬家前先结清外部合成订单，再用“回收包裹”整机打包，首次点击后在 **3 秒内**再次确认。样板库、机内内容和无线绑定一同保留，放置方法见[折叠建筑与整机搬迁](folded_buildings.md)。如果同时移动或替换了无线枢纽，展开后要重新绑定阵列。

配色页可调整颜色和本机特效档位，特效不改变产量。需要扩大接单能力时，继续阅读[万物演算核心](omni_computation_core.md)；只是需要小型补货设备时，使用[分子自动合成器](molecular_auto_crafter.md)。
