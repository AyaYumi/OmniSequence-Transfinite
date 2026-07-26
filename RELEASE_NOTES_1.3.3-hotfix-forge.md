# OmniSequence: Transfinite 1.3.3-hotfix-forge

这是面向 Minecraft 1.20.1 Forge 的稳定性与自动合成发配热修复版本，建议所有 `1.3.3-forge` 用户更新。

## 主要更新

### 重做自适应材料发配反馈

- 动态窗口不再等待机器返回成品。
- CPU 每成功发配一整份配方，就立即扩大下一轮窗口，并在同一服务器 Tick 内继续供料。
- 遇到机器拒收、供应器忙碌或内部仍有材料排队时，立即停止当前 Tick 的继续推送并缩小窗口；下一 Tick 自动重新探测。
- 慢速、高并行、大输入缓存以及只接收输入、不及时返回产物的处理目标，都可以更快填充输入缓存。

### 扩展第三方样板供应器兼容

- 普通自适应路径不再限定供应器必须是 AE2 原版的具体实现类。
- 所有正确实现 AE2 15.4.10 `ICraftingProvider` 成功、拒收和忙碌状态约定的第三方样板供应器，均可使用同 Tick 连续发配。
- 每次推送仍是一整份独立配方，由 AE2 分别扣料和记账，不会把多种材料合并成一个可能堵塞输入槽的巨型材料包。
- 已明确兼容的专用批量机器继续使用原有直推逻辑，不受本次调整影响。

### 调度安全

- 保留缓存感知的初始窗口探测。
- 保留完整数量预检，避免多材料配方只进入部分材料。
- 继续受到每核心目标耗时、全服紧急耗时和工作单元上限保护，防止超大订单长期占用服务器 Tick。
- 1.20.1 Forge 版已经包含 `BufferBuilder: Not building!` 渲染缓冲修复。

## 兼容性

- Minecraft：`1.20.1`
- Forge：`47.4.10` 或更高
- Applied Energistics 2：`15.4.10`
- ExtendedAE：`1.20-1.4.12-forge`
- Glodium：`1.20-1.5-forge`
- Advanced AE、ExtendedAE Plus、JEI、AE2WTLib：可选兼容

现有世界、样板与配置文件可以直接沿用，Mod ID 仍为 `molecularmanipulator`。

> 1.20.1 Forge 版已删除独立的“分子构序重写阵列”单方块，本次同步不会恢复该方块；相关功能继续由“构序阵列”多方块提供。

## 更新方法

删除旧的 `omnisequence-transfinite-1.3.3-forge.jar`，只保留：

```text
omnisequence-transfinite-1.3.3-hotfix-forge.jar
```

请勿同时安装两个版本，否则会因重复加载同一 Mod ID 而无法启动。
