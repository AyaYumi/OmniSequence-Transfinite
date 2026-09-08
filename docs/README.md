# Integration APIs / 接口索引

Target: OmniSequence: Transfinite **2.0.1-forge**, Minecraft **1.20.1**, Forge,
Java **17**, AE2 **15.4.10 through 15.x**, AppliedEnhancements **1.0.6-forge**.
The Mod ID remains `molecularmanipulator`.

| Integration | Contract | Documentation |
| --- | --- | --- |
| Atomic AEKey delivery to a pattern provider | `com.atir.molecularmanipulator.api.crafting`, runtime ABI 1; supported resource types depend on the provider | [Omni Batch Provider API](omni-batch-provider-api.md) |
| Generic well inputs and pattern output isolation | `MatterFabricationRecipe.aeInputs`, JSON `ae_inputs`, assembly-owned queues | [Well recipe format and limitations](matter-research-api.md) |
| Well research, prerequisites, progress administration and production bonuses | `com.atir.molecularmanipulator.research.MatterResearchApi` and data recipes | [Matter Research / KubeJS API](matter-research-api.md) |
| AELIS planning, cyclic execution and shared AE2 enhancements | Separate AppliedEnhancements mod | [AppliedEnhancements API documentation](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/docs/API_INTEGRATION.md) |

Use `compileOnly` against the relevant mod JARs, install required mods separately
at runtime, and do not shade their packages. Optional integrations must isolate
class loading until the corresponding Mod ID is present. Research mutations run
on the owning server thread; caller integrations must enforce their own permissions.
Internal `blockentity`, `mixin` and renderer implementation details are not a
general compatibility promise; use only the documented entry points.

Forge consumers must recompile for Java 17 and AE2 15. Batch ABI 1 is retained,
but NeoForge 1.21.1 binaries and `RecipeHolder`-based examples are not a binary
compatibility promise. The research API uses recipe objects directly on this branch;
see the version-specific signatures and NBT examples in the research reference.
UI enabled/highlight state is presentation data, not a new crafting or research API.

## 中文

本文档对应 2.0.1-forge，批量投料 API 保持 v1；研究接口从 2.0.0 提供。模组 ID
仍为 `molecularmanipulator`，客户端和服务端都需要 AppliedEnhancements 1.0.6-forge。

| 需求 | 使用接口 |
| --- | --- |
| 第三方样板机器接收完整 AEKey 批次，资源类型由供应器决定 | [批量供应器 API](omni-batch-provider-api.md) |
| 构筑井通用输入、样板产物隔离与当前限制 | [配方格式与已知限制](matter-research-api.md) |
| 数据包／KubeJS 研究与配方、前置等级、进度管理 | [研究 API](matter-research-api.md) |
| 调用 AELIS、管理循环合成执行 | [前置独立 API](https://github.com/AyaYumi/AppliedEnhancements/blob/1.20.1-forge/docs/API_INTEGRATION_ZH.md) |

开发时使用 `compileOnly`，运行时单独安装前置，不复制或嵌入 API 类。
可选兼容需延迟到目标模组存在后加载。研究修改在控制器所属服务端线程执行；
Java 调用方需自行校验权限。批量供应器的长期材料所有权、研究进度和 AELIS 计划
是不同契约，不能用其中一个接口替代另一个。

Forge 调用方需按 Java 17 / AE2 15 重新编译。研究接口直接接收配方对象；
1.21.1 的 `RecipeHolder`、数据组件和网络注册代码不能直接复制到本分支。
客户端按钮的开启高亮不会改变批量接口 ABI 或配方执行权限。
