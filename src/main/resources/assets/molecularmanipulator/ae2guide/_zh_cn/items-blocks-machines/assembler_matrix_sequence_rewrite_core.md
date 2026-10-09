---
navigation:
  parent: omnisequence-index.md
  title: "装配矩阵构序重写核心"
  icon: molecularmanipulator:assembler_matrix_molecular_core
  position: 1010
item_ids:
- molecularmanipulator:assembler_matrix_molecular_core
---

# 装配矩阵构序重写核心

装配矩阵构序重写核心是 ExtendedAE 装配矩阵的加工部件，用于在矩阵内执行大批量合成。它使用矩阵中的样板，不能单独运行。

<Row>
<BlockImage id="molecularmanipulator:assembler_matrix_molecular_core" scale="4" />

<BlockImage id="molecularmanipulator:molecular_manipulator" scale="4" />
</Row>

## 安装

1. 在构筑井完成**二阶：构序阵列**研究并制作核心。
2. 按 ExtendedAE 的规则搭建装配矩阵，将重写核心放在内部功能核心位置。
3. 在矩阵的样板核心中放入兼容分子装配室的样板。
4. 确认矩阵成型并连接在线 ME 网络，再从终端请求产物。

矩阵仍需要自己的结构件与样板核心。重写核心提供加工能力，材料和网络连接由完整矩阵提供。

## 工具与容器

加工按实际配方消耗材料，并保留需要返还的容器、可复用物品与工具耐久。适合批量执行的配方会合并加工；需要逐次处理的配方按单份执行。

批量加工仍需要足够的材料、合成能源和产物空间。

## 任务与输出

ME 无法接收时，产物保存在核心中等待。取消合成会停止未执行部分并退回未用材料和当前工具，已经完成的产物保留。

正常拆下核心时，材料、产物和未完成任务随核心携带。将其装回有效矩阵并恢复网络后可继续。

## 配方

<RecipeFor id="molecularmanipulator:assembler_matrix_molecular_core" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
