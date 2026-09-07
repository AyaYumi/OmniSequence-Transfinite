# AppliedEnhancements development dependency / 开发前置

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
