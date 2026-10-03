# Paired client verification / 双模组客户端验证

Run with Java 17 and the separately built Applied Enhancements 1.1.1-forge JAR in
`libs/`. This optional source set never enters a normal release JAR.

```powershell
.\gradlew.bat -I tools/client/client.init.gradle runClient --console=plain
.\gradlew.bat "-Pae2_uelm_version=15.5.4-uelm" -I tools/client/client.init.gradle runClient --console=plain
```

The runner uses only `build/client-verification-run`. It opens eight controller
pages using actual Forge/AE menu packets, checks quantum and duplication slot
filters and insertion/removal, loads client Mixins/shaders/resources and saves
screenshots below that directory. It waits for the placed host to reach the client
before opening its menu. Successful completion requires `PAIRED_CLIENT_ALL_PASS`.
Do not point the runner at a personal save. Review screenshots for visual defects;
the success marker confirms loading and slot checks, not every possible layout.

## 中文

只使用 build 下的隔离客户端目录，正式构建不包含验证类。验证真实菜单数据包、
八个页面、槽位过滤与取放、客户端 Mixin 和渲染资源，并保存截图。两套 AE 分别运行，
看到全部通过标记后仍需检查画面布局；不要使用玩家存档。
