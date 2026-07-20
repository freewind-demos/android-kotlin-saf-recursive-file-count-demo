## 简介

这个 Demo 演示：用 SAF 选一个目录后，分别用两种 API **递归统计全部子孙文件数量**。

重点不是字段细节，而是两类 API 的 **Progress 语义差异**：

1. **批式** `DocumentFile.listFiles()` — 一次返回整目录子项 → Progress 只报「当前扫哪个目录」以及「该目录拿到多少项/文件」
2. **流式** `ContentResolver.query` + `Cursor.moveToNext` — 每行都能访问 → Progress 报「当前文件名」

两种方式扫完后都显示**文件总数**。

## 快速开始

### 环境要求

- JDK 17
- Android SDK 35
- 真机或模拟器（API 26+）

### 运行

```bash
cd android-kotlin-saf-recursive-file-count-demo

# 首次：生成 Gradle Wrapper
./android-gradle-wrapper.mts

# 编译 debug APK
./android-build.mts

# 编译 + 安装 + 启动
./android-adb.mts
```

安装后：

1. 点「选择目录」→ 授权一个文件夹
2. 点「DocumentFile.listFiles（批式）」或「ContentResolver.query（流式）」
3. 看单行 Progress，结束后看文件数量

## 注意事项

- 必须经 SAF 选目录，不能直接填 `/sdcard/...`
- 大目录 Progress 会刷得很快，属正常
- 统计对象是**文件**；目录本身不计入数量

## 教程

### 1. 为什么 Progress 不一样

SAF 下列目录常见两条路：

| 方式 | API | 数据何时可见 | Progress 该报什么 |
|------|-----|--------------|-------------------|
| 批式 | `DocumentFile.listFiles()` | `listFiles` 返回后才有整数组 | 当前目录 + 本层项数/文件数 |
| 流式 | `query(buildChildDocumentsUriUsingTree)` | `moveToNext` 每前进一行就能读 | 当前文件（或目录）名 |

批式 API **没有**「每拿到一个文件就回调」的机会，硬要刷文件名只能在数组返回后再遍历——那已经不是扫描过程 Progress，而是本地循环。本 Demo 按 API 真实能力来报。

### 2. Demo 原理

1. `OpenDocumentTree` 拿到 tree URI，并 `takePersistableUriPermission`
2. 批式：对每个目录 `listFiles()` → 累加本层文件 → 再进子目录
3. 流式：对每个目录 `query` children URI → `while (moveToNext)` 区分目录/文件 → 文件立刻报名并计数 → 子目录待 cursor 关闭后再递归（避免嵌套 query）

### 3. 关键代码

- `DocumentFileBatchCounter.kt` — 批式递归计数 + 目录级 Progress
- `DocumentsContractStreamCounter.kt` — 流式递归计数 + 文件名 Progress
- `MainActivity.kt` — 选目录、触发两种计数、单行 Progress + 最终数量
