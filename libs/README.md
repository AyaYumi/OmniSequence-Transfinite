# AppliedEnhancements dependency / 编译依赖

This branch compiles against `appliedenhancements-1.1.0.jar` in this directory.
The separate prerequisite must include `AelisBatchExecutionContext` and `AelisExactCraftingPlanApi`.
Its classes are not embedded in OmniSequence. Install the same dependency build on
client and server. JARs under `libs/` are ignored by Git.

本分支使用独立的 AppliedEnhancements 1.1.0 JAR 编译，所需接口见上文。
前置单独安装，客户端和服务端使用相同构建，不嵌入本模组产物。

Optional CPU interfaces are compiled against these ignored local files:

| File | Tested API |
| --- | --- |
| `neoecoae-21.2.0.jar` | `ECOFastPathDispatchProvider` |
| `thunderbolt-batch-api.jar` | `IBatchCraftingProvider` (2.0.0-beta.4 through 2.0.3-beta) |
| `data_energistics-3.3.0-api.jar` | `CountedCraftingProviderAdapter` and its registration methods |

The Data Energistics filename is a local alias for the 3.3.0 snapshot `51b5e56`;
the public 3.3.0 release exposes the same contracts. Copy matching real mod JARs
here before building, or run `python .github/scripts/prepare_cpu_apis.py` to fetch
pinned compatible public releases. These are compile-only dependencies;
they are not embedded in the output and are optional at runtime.

上述 CPU JAR 仅提供编译接口，不嵌入产物，也不成为必需运行依赖。兼容测试使用
真实 JAR；数据能源在隔离测试目录启用，不会更改玩家整合包中禁用的模组。

The isolated Thunderbolt suite uses `thunderbolt-2.0.0-beta.4.jar` separately so
tests match the installed pack while compilation can use the compatible public API.
