# AppliedEnhancements development dependency / 开发前置

## Current exact API build

The current source requires **appliedenhancements-1.0.9-fix.jar** for compilation
and runtime (protocol 9). Copy the revised AppliedEnhancements build to `libs/`.
Both client and server must use that same build. Automatic AELIS integration
may remain disabled: an online Omni core or nexus explicitly invokes the planner.
The revised API manages its own explicit-call scope.

当前源码编译和运行均须使用修订版 **1.0.9-fix**，两端使用同一构建。
将独立前置构建产物 `appliedenhancements-1.0.9-fix.jar` 放入本目录。
自动规划开关可以保持关闭，在线核心／算枢主动调用公开 API。
旧 CI 固定提交不含本次尚未提交的前置修订；前置发布后须更新 CI 的固定提交。

## Historical 2.0.0 dependency setup

OmniSequence 2.0.0 requires **AppliedEnhancements 1.0.6+** on both sides.
The source build is pinned to 1.0.6 through `gradle.properties`.

The prerequisite is maintained in [AyaYumi/AppliedEnhancements](https://github.com/AyaYumi/AppliedEnhancements).
Build its release commit `3f63fa2908f0ba2693ebbb0bf4134b54bd38d31d` with Java 21:

```sh
git clone https://github.com/AyaYumi/AppliedEnhancements.git
git -C AppliedEnhancements checkout 3f63fa2908f0ba2693ebbb0bf4134b54bd38d31d
cd AppliedEnhancements
./gradlew --no-configuration-cache build
```

Copy `build/libs/appliedenhancements-1.0.6.jar` from that checkout into this
repository's `libs/` directory. On Windows, use `gradlew.bat`.
GitHub Actions performs the same source checkout and build automatically.

`compileOnly` supplies the public types and `localRuntime` supplies development
runs. The JAR is ignored by Git and is never embedded in the OmniSequence JAR.
Players install the prerequisite separately. Its source and release are not
published from this repository.

## 中文

本项目客户端和服务端均要求 AppliedEnhancements 1.0.6 或更高版本。
源码编译固定使用 1.0.6：从上方独立仓库检出指定提交，以 Java 21 构建，
将产物复制到本目录。远程 CI 自动执行相同步骤。

前置安装包被 Git 忽略，不重复提交、不嵌入 OmniSequence。
更新前置时同步 `gradle.properties`、CI 固定提交及本说明，并重新核对 API 兼容性。
