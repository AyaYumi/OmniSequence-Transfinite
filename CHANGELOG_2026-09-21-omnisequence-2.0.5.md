# OmniSequence: Transfinite 2.0.5 更新日志

适用版本：Minecraft 1.21.1 / NeoForge

## 本次更新

- UselessMod 多方块合金炉接入原生 BigInteger CPU 回传。
- 容量探测和正式提交之间发生动态收缩时，桥接层会对齐到完整 smart-doubling 任务并重试。
- 成功接管的中间产物直接记入绑定的 AE2 合成 CPU，避免大量 Long.MAX_VALUE 分段 ME 插入。
- 版本提升至 2.0.5。

## 整合包配置建议

在 config/useless_mod-server.toml 中建议设置：

    ae_output_return_budget_millis = 12

旧存档中已经保存的输出队列没有 CPU token，2.0.5 无法将它们重新绑定到 CPU。升级后请先取消或清理卡住的旧合成，再用小数量新任务验证 native 回传。

## 安装

1. 完全关闭客户端和服务端。
2. 保留 omnisequence-transfinite-2.0.5.jar，不要同时启用 2.0.4。
3. 启动后重新提交一个小批量合成任务。

文件 SHA-256：80A4712D665D5A6DD724F019C2DE1144CC0691DF8DB3F6952DDDDDA397523462
