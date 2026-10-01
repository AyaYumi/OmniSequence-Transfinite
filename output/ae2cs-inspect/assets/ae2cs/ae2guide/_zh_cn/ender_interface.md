---
navigation:
  parent: index.md
  title: ME末影接口
  icon: ae2cs:ender_interface
  position: 120
item_ids:
  - ae2cs:ender_interface
  - ae2cs:extended_ender_interface
  - ae2cs:ender_interface_part
  - ae2cs:extended_ender_interface_part
  - ae2cs:ender_interface_upgrade
  - ae2cs:extended_ender_interface_upgrade
---

# ME末影接口

<Row gap="16">
  <BlockImage id="ae2cs:ender_interface" scale="2" />
  <BlockImage id="ae2cs:extended_ender_interface" scale="2" />
  <ItemImage id="ae2cs:ender_interface_part" scale="2" />
  <ItemImage id="ae2cs:extended_ender_interface_part" scale="2" />
  <ItemImage id="ae2cs:ender_interface_upgrade" scale="2" />
  <ItemImage id="ae2cs:extended_ender_interface_upgrade" scale="2" />
</Row>

**ME 末影接口** 是 AECS 的一类网络部件，
功能基于原版 **<ItemLink id="ae2:interface" />** 扩展而来，
保留了接口原有的网络交互能力，
同时增加了对世界中掉落物的主动收集功能。

---

## 功能与用途

ME 末影接口可以在 ME 网络和世界环境之间建立更直接的交互。
和普通接口不同，它不仅能与相邻设备或容器交换物品，
还可以主动收集一定范围内产生的掉落物，并将其导入 ME 网络。

这个特性适合：

- 自动化采集挖掘或破坏行为产生的掉落物
- 将世界中的物品流直接并入 ME 网络
- 减少对额外漏斗或收集装置的需要

---

## 掉落物收集

ME 末影接口具备掉落物吸收能力，它的行为特性如下：

- 可收集接口周围 **1–9 格范围内** 生成的掉落物
- 掉落物会被直接导入所连接的 ME 网络
- 支持通过标记系统对可收集物品进行筛选

---

## 标记与筛选

ME 末影接口提供了筛选掉落物的标记槽位，
可构建黑名单或白名单逻辑。

- **普通 ME 末影接口**
    - 提供 **2 行**标记槽位

- **扩展 ME 末影接口**
    - 提供 **4 行**标记槽位

通过标记系统，可以精确控制哪些物品会被收集并进入网络，
避免无关物品占用存储空间。

---

## 形态与升级

ME 末影接口以方块或部件形式存在，可安装在对应的网络结构中。
也可以用升级物品扩展，获得更多标记槽位和功能容量。

普通版和扩展版基础行为一致，
差异主要在可配置的标记数量。
