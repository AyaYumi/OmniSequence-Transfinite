# Performance maintenance / 性能维护

The Forge port retains bounded work and event-driven caches from the current
NeoForge source. These rules also apply to future changes.

| Path | Work control |
| --- | --- |
| Idle research orders | Return before resolving the level, grid or storage |
| Research material preparation | Refresh once per 20 ticks; share stock and ingredient indexes across preparations in the same tick |
| Empty research refunds | Return before resolving the grid |
| Research UI stock | Cache for 20 ticks; invalidate on research, recipe-index or grid changes |
| Well recipe selection | Cache by input-port revision, recipe index and research permission revision, including a missing match |
| Structure inspection | Reuse transformed positions; regular checks every 20 ticks with explicit invalidation for edits |
| Build and dismantle | Process bounded work per tick and keep durable ownership of pending refunds |
| Independent crafting | Cap each evaluation at 64 crafts and rotate slot/output scheduling |
| Large-body collisions | Use the body index; reuse vanilla results immediately when no bodies are present |
| Radium movement collisions | Add indexed Hub surfaces only when nearby; keep the original lazy query when no extra surface is present |
| KubeJS recipe synchronization | Capture final JSON at reload, exclude runtime object graphs, and rate-limit unreadable recipes; no per-tick reflection |
| Miniature black-hole attraction | Scan nearby entities every five ticks; apply pull every tick and recheck removal, level and bounds |
| Storage-bus acceleration | Record slots during native enumeration; recheck item identity on every extraction retry |
| Planner and diagnostics | Retain node/time/search budgets; diagnostics default off and repeated failures are globally rate limited |

`ResearchMaterialOrderServiceTest.idleOrdersNeverResolveTheMachineOrNetwork`
exercises 40,000 idle ticks, including returning to idle after clearing an order,
with an uninitialized host. Resolving its level/grid state fails this regression.
The optional engine suite checks recipe-cache invalidation through both replacement
and data-pack reload, plus repeated indexed lookups and real long-count providers.

Use [the engine suite](../tools/gametest/README.md) for both upstream AE2 and UELM.
Its lookup timings describe that path only. Full modpack TPS also depends on machine
count, active recipes, attached inventories, other mods and hardware; record MSPT
in a representative disposable save before comparing changes.

The [pack runner](../tools/pack/README.md) records server START-to-END tick time
during asynchronous workflows. Tests which manually call many ticks or construct
entire blueprints execute inside the END handler, outside that timer. Single-sample
figures and setup warnings cannot establish sustained TPS. Keep raw logs and sample
counts with any performance report.

## 中文

研究下单空闲时直接返回，空回收队列也不查询网络。准备材料每 20 tick 刷新一次，
同 tick 的多个研究共享库存与材料索引。界面缓存、端口版本缓存、结构检查限频、
搭建拆除预算、64 次批量上限、输出轮询及日志限流都保留。

回归检查验证空闲路径和缓存失效，真实游戏测试覆盖两套 AE 的大数量合成与材料
所有权。单条查找的计时不能代替整个整合包的 TPS；评估实际负载时应记录机器数量、
活跃任务、外部库存和 MSPT，并使用独立测试存档。
