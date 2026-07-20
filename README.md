## 简介

这个 Demo 演示：用 SAF 选一个目录后，分别用两种 API **递归统计全部子孙文件数量**。

重点是两类 API 的 **Progress 语义**，以及信息区如何在「不另开访问 API」的前提下写出 name + size：

1. **批式** `DocumentFile.listFiles()` — Progress 报「当前目录」与「该目录拿到多少项/文件」；`listFiles` 返回的 `DocumentFile` 上读 `name` / `length()` 写入信息区
2. **流式** `ContentResolver.query` + `Cursor.moveToNext` — Progress 报「当前文件名」；同 cursor 行的 `COLUMN_SIZE` 写入信息区

「另开访问」指再调一次针对该文件的独立 API（如 `fromSingleUri` / 单文档 query）。`length()` 与 cursor 的 size 列都不算。

两种方式扫完后都显示**文件总数**。信息区固定高度，文件多时可滚动。

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
3. 上看单行 Progress，中间滚动看文件列表（name + size），结束后看文件数量

## 注意事项

- 必须经 SAF 选目录，不能直接填 `/sdcard/...`
- 大目录 Progress / 列表会刷得很快，属正常
- 统计对象是**文件**；目录本身不计入数量

## 教程

### 1. 为什么 Progress 不一样

| 方式 | API | 数据何时可见 | Progress | 信息区 size 来源 |
|------|-----|--------------|----------|------------------|
| 批式 | `DocumentFile.listFiles()` | `listFiles` 返回后才有整数组 | 当前目录 + 本层项数/文件数 | 返回的 `DocumentFile.length()` |
| 流式 | `query(buildChildDocumentsUriUsingTree)` | `moveToNext` 每前进一行就能读 | 当前文件（或目录）名 | 同行 `COLUMN_SIZE` |

### 2. Demo 原理

1. `OpenDocumentTree` 拿到 tree URI，并 `takePersistableUriPermission`
2. 批式：对每个目录 `listFiles()` → 对本层文件回调 path+length → 再进子目录
3. 流式：对每个目录 `query` → `while (moveToNext)` → 文件立刻 Progress + 回调 path+size → 子目录待 cursor 关闭后再递归

### 3. 关键代码

- `DocumentFileBatchCounter.kt` — 批式递归计数 + 目录级 Progress + 文件列表回调
- `DocumentsContractStreamCounter.kt` — 流式递归计数 + 文件名 Progress + 文件列表回调
- `MainActivity.kt` — 选目录、单行 Progress、固定高度可滚信息区、最终数量
