## 简介

这个 Demo 演示：用 SAF 选一个目录后，分别用两种 API **递归统计全部子孙文件数量**，并把**每个文件相关 API 的耗时（ms）**记下来。

1. **批式** `DocumentFile.listFiles()` — 目录级 Progress；对 `listFiles` / `isDirectory` / `isFile` / `name` / `length()` 分别计时；目录大操作另有总耗时
2. **流式** `ContentResolver.query` + Cursor — 对 `getTreeDocumentId` / `buildChildDocumentsUriUsingTree` / `query` / cursor 遍历分别计时；目录大操作另有总耗时；size 来自同行 `COLUMN_SIZE`（不另开访问）

上方「当前事件」完整显示（可换行，不截断）。下方信息区追加**全部过程日志**（含耗时），固定高度可滚。结束显示文件总数。

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
3. 上方看当前事件（完整文案）；下方滚动看全部 API 日志与耗时；结束看文件数量

## 注意事项

- 必须经 SAF 选目录，不能直接填 `/sdcard/...`
- 大目录日志会很多，属正常
- 统计对象是**文件**；目录本身不计入数量

## 教程

### 1. 计时怎么嵌套

大操作（扫某个目录）起止包住小操作（`listFiles` / `query` / `length()` 等）：

- 小操作：各自一行 `xxx()  Nms`
- 大操作：结束时再一行 `目录 … 完成  Nms`

批式对每个文件还会单独报 `length() Nms`。

### 2. Demo 原理

1. `OpenDocumentTree` 拿到 tree URI，并 `takePersistableUriPermission`
2. 批式：`listFiles` → 对每个子项调 `isDirectory`/`name`/`length` 并计时 → 目录总耗时 → 递归
3. 流式：`query` 计时 → cursor 遍历计时（size 同行读出）→ 目录总耗时 → 递归

### 3. 关键代码

- `DocumentFileBatchCounter.kt` — 批式 + 各 API 耗时
- `DocumentsContractStreamCounter.kt` — 流式 + 各 API 耗时
- `MainActivity.kt` — 当前事件完整显示 + 日志区 + 最终数量
