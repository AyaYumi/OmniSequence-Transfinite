# Integration APIs / 接口文档

Current source: OmniSequence **2.0.8-forge**, Minecraft **1.20.1**, Forge **47.4.20**,
Java **17**, AE2 **15.4.10 / UELM 15.5.4**, Applied Enhancements **1.1.0-forge+** (paired release **1.1.1**).
Mod ID: `molecularmanipulator`.

| Contract | English | 中文 |
| --- | --- | --- |
| Atomic long-count pattern delivery, admission, optional CPU contracts/adapters and output flush; runtime ABI 1 | [Batch provider SPI](omni-batch-provider-api.md) | [批量供应器 SPI](omni-batch-provider-api.zh-CN.md) |
| Exact BigInteger provider/output capabilities and original-provider identity | [Exact-count API](omni-exact-provider-api.md) | 同页中文说明 |
| Well recipe JSON, generic AEKey inputs, research, machine imports and KubeJS | [Well and research API](matter-research-api.md) | [构筑井与研究 API](matter-research-api.zh-CN.md) |

AELIS planning, cycle execution and shared AE enhancements belong to the separate
AppliedEnhancements mod. Use its [1.1.1 API documentation](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/docs/README.md). The minimum 1.1.0 runtime remains supported; new 1.1.1 API types require 1.1.1 at compile time and runtime. Omni's internal
planner/Mixin classes are not integration entry points.

Use `compileOnly` with the separate JARs and install each mod at runtime. Do not
shade or copy their API packages. Optional compatibility classes must load only
when the corresponding mod is present. Keep the original AE provider object when
registering a capability. Mutate research on the owning server thread; integrations
must enforce their own permissions. Live game-state queries also belong on that
thread unless the caller has obtained an immutable snapshot.

## 选择接口

- 接收完整批次：使用两阶段批量供应器 SPI，或注册可选适配器。
- Forge 兼容接入：配方使用 `recipes/` 与 NBT，Java 方法直接接收配方对象；可选 CPU 仅在匹配公开接口存在时启用。
- 接收超出 long 的逻辑次数：使用大数能力与精确输出接口。
- 添加配方、研究和整机导入：使用数据包/KubeJS 格式及 MatterResearchApi。
- 循环种子与批量回滚：使用 AES 的 `AelisBatchExecutionContext`，首份、追加抽料和退款共享库存视图。

最低兼容 AES 1.1.0，本次推荐搭配 1.1.1；新增的 1.1.1 查询不能直接用于旧 JAR。
游戏配置参考位于项目根目录 [CONFIGURATION.md](../CONFIGURATION.md)。
发布 JAR 的 `docs/` 仅包含本索引与 API 文档，配置参考放在 JAR 根目录。

`blockentity`、`mixin` 和渲染内部类不承诺通用二进制兼容。研究权限、长期材料
所有权和 AELIS 计划是不同契约，不能相互替代。文档采用当前实现，不保留依赖
个人机器路径的验证过程。
