---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "天枢物质复制"
  icon: molecularmanipulator:singularity_sequence_matter_bucket
  position: 1
item_ids:
- molecularmanipulator:singularity_sequence_matter_bucket
---

# 天枢物质复制

**奇点序质为流体**，可在 JEI 流体列表或对应桶上打开本页。悬停流体并按住 G 查看完整说明。

微型黑洞吞噬能量生成**奇点序质**，序质存入 ME 流体存储，再用于复制槽中的样品。

<ItemGrid>
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

## 先生产序质

1. 建成天枢，接入在线 ME 网络。
2. 为奇点序质提供可接收的 **ME 流体存储**。
3. 在**物质复制**页的微型黑洞槽放入微型黑洞，最多堆叠 **64 个**。
4. 为主控相邻六面提供可抽取 FE，或给连接的 ME 网络供给 AE，序质自动生产并写入 ME。

只放微型黑洞就能生产序质，无需样品，也无需启动资源采集。物品存储盘不能代替流体存储；使用无限存储时也要确认它支持流体。

| 微型黑洞数量 | 默认每 20 tick 序质产量 |
| --- | --- |
| 1 个 | 20 mB |
| 16 个 | 320 mB |
| 64 个 | 1280 mB |

## 能量消耗

| 能量 | 默认每 mB | 1 个微型黑洞满额一轮 |
| --- | --- | --- |
| FE | 1000 FE | 20,000 FE |
| AE | 256 AE | 5120 AE |

默认优先从主控相邻六面的能源能力抽取 FE，再使用已连接 ME 网络的 AE。两种能量按配置优先顺序使用，页面分别显示 FE 与 AE 消耗。无限磁盘提供流体容量，仍需要实际能源。

服务器配置 **视界奇点天枢 → 物质复制** 可设置能量优先级、每 mB 的费用、每个微型黑洞的产量和生产间隔。存储或能源不足时只按可生产数量处理。

## 放入样品开始复制

<Row>
<BlockImage id="molecularmanipulator:event_horizon_singularity_hub" scale="4" />
</Row>

1. 把目标物品放进**样品槽**，保留 ME 物品接收空间。
2. 网络中累计至少 **1000 mB（1 B）**奇点序质后，自动复制 1 件。
3. 复制品直接写入 ME，样品保留，其组件数据随复制品保留。
4. 取出样品即可停止复制，微型黑洞仍可继续生产序质。

| 槽位 | 允许内容 |
| --- | --- |
| 总览内量子槽 | 仅缠绕态奇点，用于连接网络 |
| 样品槽 | 待复制的物品 |
| 微型黑洞槽 | 微型黑洞，最多 64 个 |

已经产生但暂未送入 ME 的输出与待退回序质会保存并继续尝试返还。
