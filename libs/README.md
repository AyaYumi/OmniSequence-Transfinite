# Local build dependencies / 本地编译依赖

Place the JARs for a source build here. For a released mod, see the [installation guide](../README.md#install).

从源码编译时把所需 JAR 放在这里。安装发布版请看[安装步骤](../README.zh-CN.md#安装)。

## Prepare the local build inputs / 准备本地编译依赖

Build OmniSequence 21.3.0.5 for Minecraft 1.21.1 / NeoForge with Java 21 and AE2 19.2.18.

Place `appliedenhancements-1.1.4.jar` in this directory before building. Use an
AppliedEnhancements build that provides `AelisBatchExecutionContext` and
`AelisExactCraftingPlanApi`. Install the same AppliedEnhancements build separately on
the client and server, with a minimum of 1.1.2. Git ignores JARs in `libs/`.

编译前把 `appliedenhancements-1.1.4.jar` 放到这里。运行时客户端和服务端单独安装同一版本的
AppliedEnhancements，最低为 1.1.2，须提供上面的两个接口。

Gradle uses these JARs at compile time only. Install the matching mods at runtime
for the optional CPU integrations you use.

| File | Tested API |
| --- | --- |
| `neoecoae-21.2.19.jar` | `ECOExternalCrafting`, registered providers, and the external scheduler API |
| `thunderbolt-batch-api.jar` | `IBatchCraftingProvider` (2.0.0-beta.4 through 2.0.3-beta) |
| `data_energistics-3.3.0-api.jar` | `CountedCraftingProviderAdapter` and its registration methods |

Run `python .github/scripts/prepare_cpu_apis.py` from the repository root to fetch
the pinned releases, or copy matching mod JARs under the names in the table.
For Data Energistics, the public 3.3.0 release and snapshot `51b5e56` of 3.3.0
provide the same APIs.

In `gradle.properties`, `neoeco_api_build=21.2.19` selects the ECO build JAR.
The same value sets the minimum when you install ECO. The optional
dependency uses `ordering="NONE"`.

The helper validates the ECO mod ID, version and integration class. Before the release is available, place the companion NeoECOAE build here or set `NEOECO_API_JAR_URL`.

编译时将发布 JAR 按表中名称放到这里，也可以在仓库根目录运行 `python .github/scripts/prepare_cpu_apis.py` 下载固定版本。
运行时按需单独安装这些模组。ECO 为可选依赖，安装时最低为 21.2.19。

For runtime integration versions, see the [author reference](../docs/README.md).

可选联动的运行版本见[作者参考](../docs/README.md)。
