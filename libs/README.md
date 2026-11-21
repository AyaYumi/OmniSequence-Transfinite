# Local Forge dependencies

Build OmniSequence 20.3.0.3 for Minecraft 1.20.1 / Forge with Java 17.

Place `appliedenhancements-1.1.4-forge.jar` in this directory before building. To use
another compatible API build, set `-Papplied_enhancements_api_build=<version>` and supply
its matching JAR. The JAR must provide the cycle transaction, exact-plan and
smart-doubling APIs.

Install the same Forge build of AppliedEnhancements separately on client and server.
The runtime range is `[1.1.2-forge,1.2)`. The `applied_enhancements_version` property
sets its lower bound.

源码编译需要把 `appliedenhancements-1.1.4-forge.jar` 放到这里。运行时客户端和服务端单独安装
同一版本的 AppliedEnhancements，须满足 `[1.1.2-forge,1.2)` 和上述接口要求。

Gradle resolves AE2 15.4.10 from Modrinth Maven and ExtendedAE 1.20-1.4.20-forge
from CurseMaven (`curse.maven:extendedae-892005:8843577`). See the
[upstream GitHub release](https://github.com/GlodBlock/ExtendedAE/releases/tag/1.20-1.4.20-forge).

Gradle resolves NeoECOAE 20.5.10 from Modrinth Maven. The `neoeco_api_build` property
selects the build artifact and sets the optional runtime minimum. For an unpublished
build, pass `-Pneoeco_api_repository=<local Maven repository>` with the same artifact
coordinates. To use the optional integration, install Forge NeoECOAE 20.5.10 or later.

Thunderbolt and Data Energistics use the Java 17 declarations in
`src/optionalCpuApi/java` at compile time. Install the matching Forge mod JARs to
use these integrations. Thunderbolt 2.0.5 for Forge supplies the batch executor
interface for native dispatch. Builds without that interface use the existing
dispatch path. Lightning Tech batch probe acceleration targets the Forge 2.1.4
build.

The `adaptive` game-test fixture uses ExtendedAE Plus **1.6.2 for Forge**
(`extendedae_plus-1.6.2.jar`). For runtime integration versions, see the [author reference](../docs/README.md).

使用可选 CPU 联动时单独安装对应的 Forge 模组。安装 ECO 时最低要求 20.5.10。
`adaptive` 游戏测试使用 Forge 版 ExtendedAE Plus **1.6.2**。可选联动的运行版本见[作者参考](../docs/README.md)。
FTB Teams 可选，未安装时使用玩家 UUID 绑定；旧版无绑定黑白洞需要重新放置。
