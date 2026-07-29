# OmniSequence: Transfinite 1.3.6 Release Notes

Release date: 2026-07-29

Target environment: Minecraft 1.20.1 / Forge 47.4.10+ / Applied Energistics 2 15.4.10

## Highlights

Version 1.3.6 adds in-game AE2 GuideME documentation for the Forge build's three registered primary
blocks and improves the Sequence Array Controller's safety and pattern-management
workflow.

## Sequence Array Controller UI

- The previous-page and next-page buttons are wider and now separate **Build**
  from **Dismantle**.
- **Dismantle** uses a timed two-step confirmation. The first click only arms
  the action; the second click is accepted after a short debounce and within
  three seconds.
- Rapid double-clicks are ignored. Clicking another control or slot, or letting
  the timer expire, cancels the confirmation.

## Pattern Inventory

- Pattern slots accept encoded AE2 crafting, smithing, and stonecutting patterns.
- Processing patterns, blank patterns, and invalid patterns are rejected.
- Shift-moving a supported pattern fills the current page first and continues
  into later pages when the current page is full.
- Unsupported encoded patterns cannot fall through into the matter-rewrite
  blueprint slot.
- Unsupported patterns already present in an older world are preserved and can
  be removed, but they are not advertised to the ME Network as usable recipes.

## In-Game Guide

AE2 GuideME pages are available for:

- Assembler Matrix Sequence Rewrite Core
- Omni-Computation Core
- Sequence Array Controller

Hover one of these blocks in an inventory or recipe viewer and press `G` to open
its guide page.

## Compatibility

The release is built for Minecraft 1.20.1 with Forge 47.4.10 and AE2 15.4.10.
The standalone Molecular Sequence Rewrite Array remains intentionally absent
from this branch; its GuideME page is therefore not shipped. The Expanded AE
2.1.1 conflict declaration from the Minecraft 1.21.1 NeoForge build is also not
copied because that mod version and Mixin target belong to a different loader
and AE2 line.

## Installation and Upgrade

1. Fully stop the client and server, then back up the world and configuration.
2. Remove or disable every older active `omnisequence-transfinite-*.jar`.
3. Put `omnisequence-transfinite-1.3.6.jar` in both client and server `mods`
   directories.
4. Keep exactly one active OmniSequence JAR in each `mods` directory.
5. Start the game and verify the loaded mod version is `1.3.6`, then smoke-test
   GuideME with `G`, Shift-moving patterns, and Dismantle confirmation.

This update does not require a new world or deletion of existing configuration.

SHA-256: `74AF2270B0FFE5441AFD182D01BA8B7DA11BAB9FFF2937B74FD18EDCAFA8CC4B`

---

# 万象构序：超限 1.3.6 更新说明

发布日期：2026-07-29

适用环境：Minecraft 1.20.1 / Forge 47.4.10+ / Applied Energistics 2 15.4.10

## 版本亮点

1.3.6 为 Forge 构建中仍注册的三个主要方块补充了 AE2 GuideME 游戏内文档，并改进了构序阵列控制器的
操作安全性和样板管理流程。

## 构序阵列控制器界面

- 上一页和下一页按钮已经加宽，并移动到“一键安放”和“一键拆卸”之间。
- “一键拆卸”改为限时二次确认：首次点击只进入确认状态，短暂防抖后必须在三秒内再次点击。
- 快速双击不会启动拆卸；点击其他控件或槽位、等待超时都会取消确认。

## 样板库存

- 样板槽接受 AE2 编码合成、锻造及切石样板。
- 处理样板、空白样板和失效样板会被拒绝。
- Shift 快捷放入会优先填充当前页面；当前页满后会继续放入后续页面。
- 不受支持的编码样板不会再误入物质重写的蓝图样品槽。
- 旧世界中已经存在的不兼容样板不会被删除，仍可取出，但不会作为可用配方向 ME 网络发布。

## 游戏内文档

以下方块已经提供 AE2 GuideME 页面：

- 装配矩阵构序重写核心
- 万物演算核心
- 构序阵列控制器

在物品栏或配方查看器中指向对应方块并按 `G`，即可打开其说明页面。

## 兼容性

本版本基于 Minecraft 1.20.1、Forge 47.4.10 和 AE2 15.4.10 构建。
本分支仍按既有兼容策略移除独立的“分子构序重写阵列”单方块，因此不发布对应 GuideME 页面。
Minecraft 1.21.1 NeoForge 构建针对 Expanded AE 2.1.1 的冲突声明也不会照搬：
该版本及其 Mixin 目标属于不同的加载器和 AE2 版本线。

## 安装与升级

1. 完全关闭客户端和服务端，并备份世界与配置。
2. 删除或停用所有旧版且仍处于启用状态的 `omnisequence-transfinite-*.jar`。
3. 将 `omnisequence-transfinite-1.3.6.jar` 放入客户端和服务端的 `mods` 目录。
4. 确保每个 `mods` 目录中只有一个启用中的万象构序 JAR。
5. 启动游戏并确认加载的模组版本为 `1.3.6`，然后烟测按 `G` 打开 GuideME、
   Shift 快捷放入样板和拆卸二次确认。

本次更新不要求新建世界，也不要求删除现有配置。

SHA-256：`74AF2270B0FFE5441AFD182D01BA8B7DA11BAB9FFF2937B74FD18EDCAFA8CC4B`
