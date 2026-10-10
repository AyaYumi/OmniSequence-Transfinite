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

微型黑洞在天枢中消耗能源，生成**奇点序质流体**。天枢从 ME 流体存储取用序质，复制样品槽中的物品。

<ItemGrid>
<ItemIcon id="molecularmanipulator:black_hole" />
<ItemIcon id="molecularmanipulator:singularity_sequence_matter_bucket" />
</ItemGrid>

## 生产奇点序质

1. 建成[视界奇点天枢](event_horizon_singularity_hub.md)，或连接[单方块奇点天枢](compact_singularity_hub.md)。
2. 为网络提供能接收奇点序质的 **ME 流体存储**。
3. 在**物质复制**页的微型黑洞槽放入最多 **64 个微型黑洞**。
4. 提供 FE 或 AE 能源，序质会自动生产并存入 ME。

无需放入样品或启动资源采集即可生产序质。物品存储不能代替流体存储，使用无限存储时也要确认其支持流体。

| 黑洞数量 | 默认每 20 tick 产量 |
| --- | --- |
| 1 个 | 20 mB |
| 16 个 | 320 mB |
| 64 个 | 1280 mB |

## 能源

默认优先从控制器相邻六面的能源设备抽取 FE，再使用 ME 网络中的 AE。相邻能源设备需要允许抽取。

| 能源 | 每 mB 费用 | 一个黑洞满额生产一轮 |
| --- | --- | --- |
| FE | 1000 FE | 20,000 FE |
| AE | 256 AE | 5120 AE |

物质复制页分别显示 FE 与 AE 消耗。服务器的**视界奇点天枢 → 物质复制**配置可调整能源优先级、费用、产量和生产间隔。能源与流体空间不足时，实际产量会降低或暂停。

## 复制物品

把目标物品放入**样品槽**，并为复制品预留 ME 物品空间。每复制一件消耗 **1000 mB（1 B）**奇点序质，复制品进入 ME，原样品保留，物品的组件数据也会保留。

取出样品即可停止复制。槽中的微型黑洞仍会继续生产序质。网络暂时无法接收时，已经产生的输出与待退回序质保留，等待恢复。

总览页的量子槽用于缠绕态奇点，不用于放置样品或微型黑洞。
