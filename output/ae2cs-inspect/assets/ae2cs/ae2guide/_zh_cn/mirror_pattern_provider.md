---
navigation:
  parent: index.md
  title: 镜像样板供应器
  icon: ae2cs:mirror_pattern_provider
  position: 175
item_ids:
  - ae2cs:mirror_pattern_provider
  - ae2cs:mirror_pattern_provider_part
  - ae2cs:mirror_linker
---

# 镜像样板供应器

<Row gap="16">
  <BlockImage id="ae2cs:mirror_pattern_provider" scale="2" />
  <ItemImage id="ae2cs:mirror_pattern_provider_part" scale="2" />
  <ItemImage id="ae2cs:mirror_linker" scale="2" />
</Row>

**镜像样板供应器** 会复制另一台样板供应器提供的样板与优先级。它自身没有样板槽，可以让同一套样板配置同时用于多个加工位置。

---

## 绑定目标

使用 **<ItemLink id="ae2cs:mirror_linker" />** 配置镜像：

1. 对作为来源的样板供应器使用镜像绑定工具。
2. 使用已绑定的工具点击镜像样板供应器。
3. 镜像样板供应器会立即向 ME 合成服务提供来源供应器中的样板。

工具提示会显示已记录的维度、坐标和部件所在面。手持工具对空气使用，可以清除工具中保存的目标。

镜像样板供应器不能将另一台镜像样板供应器设为来源。

---

## 工作行为

- 样板仍由来源供应器保存和配置。
- 合成请求会从镜像所在位置发配，同时使用来源供应器的配置选项。
- 镜像会跟随来源供应器的优先级。
- 来源不可用或所在区块未加载时，被镜像的样板会暂时不可用。
- 方块形态与线缆部件形态具有相同的镜像功能。

需要在多个机器组中使用同一套样板时，可以布置多个镜像样板供应器，无需手动复制样板物品。
