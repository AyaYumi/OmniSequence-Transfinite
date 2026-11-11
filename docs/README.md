# Integration APIs / 接口文档

Current source: OmniSequence **2.0.14**, Minecraft **1.21.1**, NeoForge **21.1.220+**,
Java **21**, AE2 **19.2.17+**, Applied Enhancements **1.1.2+** (compile API **1.1.4**).
Mod ID: `molecularmanipulator`.

| Contract | English | 中文 |
| --- | --- | --- |
| Atomic long-count pattern delivery, admission, optional adapters and output flush; runtime ABI 1 | [Batch provider SPI](omni-batch-provider-api.md) | [批量供应器 SPI](omni-batch-provider-api.zh-CN.md) |
| Exact BigInteger provider/output capabilities and original-provider identity | [Exact-count API](omni-exact-provider-api.md) | 同页中文说明 |
| Well recipe JSON, generic AEKey inputs, research, machine imports and KubeJS | [Well and research API](matter-research-api.md) | [构筑井与研究 API](matter-research-api.zh-CN.md) |

AELIS planning, cycle execution and shared AE enhancements belong to the separate
AppliedEnhancements mod. Use its [API documentation](https://github.com/AyaYumi/AppliedEnhancements/blob/1.21.1-neoforge/docs/README.md).
This branch compiles against 1.1.4 and supports runtime 1.1.2 or later. Omni's
internal planner/Mixin classes are not integration entry points.

Use `compileOnly` with the separate JARs and install each mod at runtime. Do not
shade or copy their API packages. Optional compatibility classes must load only
when the corresponding mod is present. Keep the original AE provider object when
registering a capability. Mutate research on the owning server thread; integrations
must enforce their own permissions. Live game-state queries also belong on that
thread unless the caller has obtained an immutable snapshot.

## 选择接口

- 接收完整批次：使用两阶段批量供应器 SPI，或注册可选适配器。
- 接收超出 long 的逻辑次数：使用大数能力与精确输出接口。
- 添加配方、研究和整机导入：使用数据包/KubeJS 格式及 MatterResearchApi。
- 循环种子与批量回滚：使用 AES 的 `AelisBatchExecutionContext`，首份、追加抽料和退款共享库存视图。

使用 AES 1.1.4 编译，运行时最低要求 1.1.2；前置单独安装，不嵌入本模组。
游戏配置参考移至项目根目录 `CONFIGURATION.md`。

`blockentity`、`mixin` 和渲染内部类不承诺通用二进制兼容。研究权限、长期材料
所有权和 AELIS 计划是不同契约，不能相互替代。
