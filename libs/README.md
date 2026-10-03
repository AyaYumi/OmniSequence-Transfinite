# AppliedEnhancements dependency / 开发前置

Current source requires the revised **1.1.0-forge** build on client and server.

1. Obtain or build the independent AppliedEnhancements 1.1.0-forge revision.
2. Copy `appliedenhancements-1.1.0-forge.jar` into this directory.
3. Run the normal Gradle build. The API validation fails clearly if the file or
   `AelisBatchExecutionContext` or `AelisSmartDoublingApi` is missing.

The runtime and compile-time declarations use `gradle.properties`. JARs under
`libs/` are ignored by Git and never shaded into the mod. Install each prerequisite
separately. Do not rename an older prerequisite to 1.1.0-forge.

Remote CI builds the matching prerequisite from AppliedEnhancements commit
`7a15a9bd434de1362a75a72aa93a010c8332d8a4`. The shared API is checked before compilation, and CI tests the upstream-built prerequisite JAR with both AE implementations. See
[development](../docs/development.md).

## 中文

需要修订版 1.1.0-forge；将实际构建放入此目录，两端使用相同版本。前置构建独立维护，
不在本仓库发布，也不嵌入 OmniSequence。旧 1.0.x 缺少共享事务类型，改文件名
不能替代接口更新。远程 CI 默认构建固定 Git 提交的匹配前置，用同一份原版 AE 构建的前置分别验证两套 AE 实现。
