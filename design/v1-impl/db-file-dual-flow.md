# DB 与同步文件夹双向数据流设计

让 DB 里的数据导出到同步文件夹；让同步文件夹内容可填充到 db 中。

Background: 

1. 需要导出所有信息到同步文件夹
2. 同步文件夹作为多端同步的 gold truth, 需要处理从 同步文件数据 -> db 的反向过程


## 方案 （结合 Gemini 3.8 flash）

构建一个以“IM 对话流”为载体、同时支持**本地数据库高性能查询**与**文件系统用户可读备份**的个人记录软件（类似 Logseq/Obsidian 与 Telegram 的结合体），核心需要解决三个工程问题：**双写一致性（Dual-Write Problem）**、**文件粒度与格式设计**、以及 **Android 存储（SAF）的性能瓶颈**。

直接在 UI 线程或同一个业务事务中“先插数据库，再写文件”通常是反模式，极易造成卡顿和数据不同步。以下是业界推荐的**最佳架构方案**：

---


### 一、 核心架构原则：单向数据流与 Outbox（发件箱）模式

**将 SQLite (SQLDelight) 作为唯一的真实数据源（SSOT, Single Source of Truth），文件系统作为异步的“投影层（Projection）”。**

不要尝试在文件和数据库之间做双向强一致性事务（在移动端成本极高且容易损坏），应采用 **事件驱动/异步写入**：

```
UI -> 触发新建/发送 
       ↓
[SQLDelight 事务] 
 ├─ 写入 Topic / Message 表
 └─ 写入 FileExportOutbox 表 (标记 topic_id, action: INSERT/UPDATE/DELETE)
       ↓
[异步后台 Worker / Flow]
 ├─ 监听/消费 Outbox
 ├─ 防抖 (Debounce) 合并同一 Topic 的频繁写操作
 └─ 序列化生成文件，写入文件系统
```

#### 为什么必须解耦异步写入？
1. **Android SAF (Storage Access Framework) 性能极慢**：如果要支持用户自选文件夹（例如配合 Syncthing/Nextcloud/坚果云 同步），必须走 SAF，其跨进程 I/O 延迟通常在数十到上百毫秒，同步写必定导致 UI 丢帧。
2. **防崩溃损坏**：如果写入文件时 App 被杀，数据库因为有事务保护不会损坏，下次启动时扫描 `Outbox` 表即可继续重试补写。

---

### 二、 文件存储粒度与格式设计

#### 1. 绝对不要“一条 Message 一个文件”
* 个人记录软件的 Message 数量增长极快，数万个小文件会耗尽 Android 文件系统的 inode，导致相册/文件管理器扫描崩溃、甚至触发系统级卡顿。

#### 2. 推荐方案：**一个 Topic 一个文件**（或按天/月归档）
* **层级设计**：
  ```text
  Backups/
  ├── topics/
  │   ├── topic_1001_读书笔记.md (或 .jsonl)
  │   └── topic_1002_灵感碎片.md
  └── metadata.json (可选：存储全局配置、标签索引)
  ```
* **格式选择**：
  * **方案 A：Markdown + YAML Frontmatter（最推荐，如果追求“人眼可读与工具互通”）**
    * 契合 Obsidian/Logseq 用户的心理模型。
    * Header 放 Topic 元数据，Body 按时间流追加 Message：
      ```markdown
      ---
      id: 1001
      title: 读书笔记
      created_at: 2026-03-01T10:00:00Z
      ---

      - **10:05** 这是一条即时记录 #tag
      - **10:12** 另一条消息内容，支持多行...
      ```
  * **方案 B：JSON Lines (`.jsonl`)（如果追求“无损还原与备份”）**
    * 每一行是一个独立的 JSON 字符串。
    * 优点：追加写入极快（`Append` 即可，不需要重写整个文件）；解析极其方便，支持精准的字段类型恢复。

---

### 三、 落地实现方案细节

#### 1. 数据库定义 (SQLDelight)
在你的 `.sq` 文件中，利用 SQLite 事务保证消息与导出任务原子性：

```sql
-- Message.sq
CREATE TABLE message (
    id TEXT PRIMARY KEY,
    topic_id TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE file_sync_task (
    topic_id TEXT PRIMARY KEY,
    updated_at INTEGER NOT NULL
);

-- 插入消息的同时触发导出任务更新（利用 SQLite 的 REPLACE 自动去重）
insertMessageWithSync:
INSERT INTO message(id, topic_id, content, created_at)
VALUES (?, ?, ?, ?);

markTopicDirty:
INSERT OR REPLACE INTO file_sync_task(topic_id, updated_at)
VALUES (?, ?);
```

#### 2. 序列化与写入流水线（Kotlin Coroutines + Flow）
在 Kotlin 业务层，通过协程做**批量与防抖处理**：

```kotlin
class BackupSyncEngine(
    private val database: AppDatabase,
    private val storageManager: ExternalStorageManager,
    private val scope: CoroutineScope
) {
    init {
        scope.launch(Dispatchers.IO) {
            // 监听 tasks 表变化
            database.fileSyncTaskQueries.selectAll()
                .asFlow()
                .mapToList(Dispatchers.IO)
                .debounce(500) // 500ms 防抖，防止用户连续快速发多条消息时频繁写同一个大文件
                .collect { tasks ->
                    for (task in tasks) {
                        syncTopicToFile(task.topic_id)
                        database.fileSyncTaskQueries.deleteTask(task.topic_id)
                    }
                }
        }
    }

    private suspend fun syncTopicToFile(topicId: String) {
        val topic = database.topicQueries.getById(topicId).executeAsOne()
        val messages = database.messageQueries.getByTopic(topicId).executeAsList()
        
        // 1. 序列化为 Markdown 或 JSON
        val content = serializeTopicToMarkdown(topic, messages)
        
        // 2. 写入文件（建议原子写入：先写临时文件 .tmp，再 rename，防止断电半包文件）
        storageManager.writeAtomically(fileName = "${topic.id}_${topic.title}.md", content)
    }
}
```

#### 3. 应对文件写入冲突（原子写）
写入 Android 外部存储或 SAF 时，如果直接覆盖写，中途断电/进程被杀会导致文件变成空内容或半截损坏。
* **最佳做法**：
  1. 在同一目录下先写入 `topic_123.md.tmp`。
  2. 刷新到磁盘缓冲（`fd.sync()`）。
  3. `renameTo("topic_123.md")`（在同分区下 Rename 是 POSIX 原子操作）。

---

### 四、 避坑指南（Android 特殊性）

1. **存储路径选择**：
   * **方案 1（体验最好，但需引导）：** 让用户通过 SAF (`ACTION_OPEN_DOCUMENT_TREE`) 选择外部目录（如 `Documents/MyNotes`）。获得持久 URI 权限后，第三方同步工具（Syncthing 等）可以直接访问这个文件夹。
   * **方案 2（最省心，但有卸载丢失风险）：** 写入应用私有外部存储 `context.getExternalFilesDir(null)`。用户连电脑时可见，免动态权限，I/O 性能远优于 SAF，但 App 卸载时会被系统清除。
2. **编辑与撤销（Update / Delete）**：
   * 因为采用的是“全量 Topic 重写”策略（一个 Topic 文件包含其所有 Messages），只要消息表中发生 Edit 或 Delete，只需同样把该 `topic_id` 放进同步队列即可，无需在文件中执行复杂的位置查找和文本替换。
3. **性能兜底**：
   * 如果某个 Topic 累计了上万条消息，每次发消息都重写一次整个文件会变慢。
   * 如果预计消息量极大，应退化为 **Append-only 的 JSON Lines**，或者**按月份分块存储**（如 `topic_1001_2026_03.md`）。

### 总结推荐
1. **存储模型**：1 个 Topic = 1 个 Markdown/JSONL 文件。
2. **写入架构**：SQLDelight 承担事务 -> 记录 Dirty Topic -> Kotlin Flow 防抖 500ms -> 异步原子写入目标文件。
3. **定位原则**：数据库管**运行效率与查询**，文件层管**持久可读与数据所有权**。


> Q: 为啥需要一个 outbox? 不能直接监听 1 个 flow，来写吗？我可能还需要一种反向的模式：假设用户重装了系统，或者用户在新机器上；它同步了数据，但是 app 是新的，这时需要反过来基于数据来构建 db，你这个模式可行吗

你的两个直觉都非常敏锐，直接触及了**端侧离线优先（Local-First）**架构的核心痛点。

针对你的两个疑问，我们逐一拆解：

---

### 一、 为什么不能直接监听一个 Flow，而非要搞个 Outbox？

直接用 Kotlin 的 `SharedFlow`、`Channel`，或者直接监听 SQLDelight 的 `messageQueries.selectAll().asFlow()` 确实最直观，但有三个在 Android 上非常致命的缺陷：

#### 1. 进程死亡与写入丢失（最核心的原因）
* 用户在 IM 界面发完一条消息，可能 **0.1 秒内**就按 Home 键切出或者直接划掉后台。
* Android 的写文件（尤其是通过 SAF 访问外部存储）非常耗时。如果是纯内存的 `Flow` / `Channel`，**一旦进程被杀，内存中的待写入事件全部丢失**。
* 结果就是：数据库里有这条记录，但文件系统里漏掉了。你无法知道漏了哪一条。
* **有了持久化的 Outbox 表**：写库与记录 Outbox 在同一个 SQLite 本地事务中完成（耗时 < 5ms）。只要数据库写成了，哪怕下一毫秒手机断电，下次冷启动时从 Outbox 表一读，就能把没写完的文件补上（保证 **At-least-once 投递**）。

#### 2. SAF I/O 报错重试与退避
* 如果用户拔掉了 SD 卡、修改了目录权限、或者 Syncthing 正在占用该文件导致文件锁冲突，直接写文件会抛出 `IOException`。
* 内存 Flow 抛异常后，事件丢了就是丢了；而 Outbox 表可以记录 `retry_count`，等网络恢复、文件解锁后再静默重试。

#### 3. “究竟是哪个 Topic 变了？”
* SQLDelight 提供的响应式 Flow 是**表级别**的通知。当 `message` 表发生插入时，Flow 只会告诉你“message 表更新了”，不会告诉你“具体是 topic_id = 1001 增加了数据”。
* 如果没有标记，你要么全量重新扫描生成所有文件，要么在应用层手写很多维护状态的代码。

> **轻量化妥协方案（如果你不想单独建 Outbox 表）：**  
> 你不需要单独建一个 `file_sync_task` 表，可以直接在 `topic` 表加一个时间戳字段：  
> `last_updated_at` 和 `last_synced_at`。  
> 只要发消息，就让 `topic.last_updated_at = now()`。  
> 写入引擎只需要查询：`SELECT * FROM topic WHERE last_updated_at > last_synced_at`。写完文件后，更新 `last_synced_at`。这也算是一种隐式 Outbox，简单且非常健壮。

---

### 二、 反向模式：新机器/重装系统，能从文件反向构建 DB 吗？

**完全可行，而且这是这类架构最优雅的地方（这正是 Obsidian、Logseq 等工具在做的“文件即数据库，SQLite 仅充当索引加速缓存”的模型）。**

如果用户换了手机，通过云同步把文件拉下来，此时新装的应用 SQLite 是空的。系统启动时，应该有一个 **“冷启动重建索引（Reindex / Rehydration）”** 的过程。

要让这套模式完美支持“文件 -> 数据库”的反向构建，你必须在数据结构和写入逻辑上遵守以下 **4 条铁律**：

#### 1. 主键必须使用 UUID / NanoID，严禁依赖 SQLite 自增 ID
* 如果你用自增 ID（1, 2, 3...），A 机器生成的自增 ID 在 B 机器重装解析时极易错乱或发生冲突。
* `topic_id` 和 `message_id` 必须在生成时就是全局唯一的字符串（如 UUIDv4 或 NanoID）。

#### 2. 文件必须包含完整的元数据与结构
文件不能只写内容，必须包含 ID、时间戳、状态等能够无损还原一条 Message 的所有字段。

* **如果是 JSON Lines（`.jsonl`，反向解析最稳妥，速度极快）：**
  ```json
  {"id": "msg_01H...", "topic_id": "top_01H...", "time": 1709280000, "text": "记录一条内容"}
  {"id": "msg_01H...", "topic_id": "top_01H...", "time": 1709280060, "text": "第二条内容"}
  ```
  *反向恢复策略*：遍历目录下的每个 `.jsonl`，按行 `json.decodeFromString`，直接批量 `INSERT`。

* **如果是 Markdown（人类可读性最好，解析稍重）：**
  ```markdown
  ---
  topic_id: "top_uuid_1001"
  title: "读书心得"
  created_at: 1709280000
  ---

  <!-- msg: id="msg_uuid_001" time="1709280000" -->
  第一条消息内容，支持换行...

  <!-- msg: id="msg_uuid_002" time="1709280060" -->
  第二条消息内容...
  ```
  *反向恢复策略*：利用正则表达式或者标记行，把 Markdown 分块解析回数据对象。

#### 3. 规避“无限乒乓循环（Ping-Pong Effect）”

这是做双向同步最容易踩的死循环大坑：
```
文件变动 -> 解析出数据 -> 插入数据库 -> 触发 Outbox / Flow -> 又去写文件 -> 文件又变动...
```
**解决方案：**
反向构建（导入）时，必须**短路（Suppress）**导出逻辑：
```kotlin
suspend fun rehydrateDatabaseFromFiles(directory: File) {
    // 方案 A: 传入一个标志位禁用同步
    SyncEngine.isSyncEnabled = false
    
    database.transaction {
        directory.walkTopDown().filter { it.extension == "md" }.forEach { file ->
            val (topic, messages) = parseFile(file)
            database.topicQueries.insertOrIgnore(topic)
            messages.forEach { database.messageQueries.insertOrIgnore(it) }
        }
    }
    
    // 方案 B: 还原完成后，直接把所有 topic 的 last_synced_at 刷成最新，不触发写入
    SyncEngine.isSyncEnabled = true
}
```

#### 4. 极端高效的冷启动性能

SQLite 在移动端的批量写入性能非常夸张：
* 如果不用事务逐条插入 10,000 条消息，需要花几十秒甚至引起 ANR；
* 但如果**包在一个 `database.transaction { ... }` 内部完成**，10,000 条数据的反向恢复在普通手机上**只需 300~500 毫秒**，用户甚至感知不到这个耗时。

### 总结整个闭环

| 阶段                | 流向             | 核心机制                                                                                 |
| :------------------ | :--------------- | :--------------------------------------------------------------------------------------- |
| **平时记录**        | UI -> DB -> File | DB 是真实源，异步带防抖与原子重命名写入文件；即使闪退，Outbox / 时间戳比对保证数据不漏。 |
| **重装系统/冷启动** | File -> DB       | 扫描备份目录，解析文件元数据，开启单次事务全量/增量填充 SQLite，禁用向外同步钩子。       |

这套方案完全可行，也是目前兼顾**移动端极致流畅的 UI 交互**与**用户对数据 100% 绝对掌控权（Local-First）**的最佳实践。

> Q: 我每条记录都有一个 attributes_updated_at，本来是用于 power sync 的，现在用在这个需求上，是否可行?


**完全可行，而且这是非常优雅的解法！**

你既然引入了 **PowerSync**，说明系统本身已经具备了成熟的**离线优先（Local-First）**基因。PowerSync 赖以生存的基础就是单行数据的变更追踪，而 `attributes_updated_at`（或系统自带的更新时间戳）正是最理想的**增量同步高水位线（Watermark）**。

借助这个字段，你可以**彻底摒弃单独的 `file_sync_task`（Outbox）表**，让数据模型更加精简。

以下是结合 PowerSync 与文件备份落地的具体设计：

---

### 一、 顺向流：如何用 `attributes_updated_at` 触发写文件？

要判断“哪些文件需要更新”，核心是比较两个时间：
1. **数据修改时间**：`attributes_updated_at`
2. **文件上次导出时间**：`last_exported_at`

只要 `attributes_updated_at > last_exported_at`，就说明文件过时了，需要重写。

#### 1. 新建一个纯本地的状态记录（不要同步到云端）
因为“文件备份到了哪个进度”是当前设备本地的事，**不需要也不应该上传到 Postgres**。建议在 SQLDelight 中定义一个**不参与 PowerSync 同步**的纯本地表：

```sql
-- local_file_export_state.sq
CREATE TABLE local_file_export_state (
    topic_id TEXT PRIMARY KEY,
    last_exported_at TEXT NOT NULL -- 记录对应 Topic 最近一次成功写入文件的时间戳
);

-- 查找哪些 Topic 变脏（Dirty）了
findDirtyTopics:
SELECT t.id 
FROM topic t
LEFT JOIN local_file_export_state s ON t.id = s.topic_id
WHERE t.attributes_updated_at > COALESCE(s.last_exported_at, '')
   OR EXISTS (
       SELECT 1 FROM message m 
       WHERE m.topic_id = t.id 
         AND m.attributes_updated_at > COALESCE(s.last_exported_at, '')
   );
```

#### 2. 一个关键优化：IM 的“会话冒泡”机制
在 IM 软件中，只要某个 Topic 内新增或修改了一条消息，通常该 Topic 自身的“最新消息时间/最后活跃时间”本来就应该被刷新。
* **做法**：插入消息时，顺手将该 `topic.attributes_updated_at` 更新为当前时间。
* **收益**：上述的 SQL 查脏语句就不用做复杂的 `EXISTS (SELECT 1 FROM message ...)` 联表了，直接退化为极速查询：
  ```sql
  SELECT t.id FROM topic t
  LEFT JOIN local_file_export_state s ON t.id = s.topic_id
  WHERE t.attributes_updated_at > COALESCE(s.last_exported_at, '');
  ```

#### 3. 消费流程
写文件的逻辑依然保持异步防抖（Debounce），文件写盘成功后，仅需更新本地表：
```kotlin
// 成功写完 topic_xxx.md
database.localFileExportStateQueries.upsert(
    topic_id = topic.id,
    last_exported_at = topic.attributes_updated_at
)
```

---

### 二、 反向流：重装/迁移时，从文件反向重建 DB

有了 `attributes_updated_at`，反向恢复（File -> DB）不仅可行，还能天然支持**基于时间戳的冲突解决（LWW, Last-Write-Wins）**。

#### 1. 文件的序列化要求
在导出的 Markdown（Frontmatter 中）或 JSONL 中，必须把 `attributes_updated_at` 一并持久化：
```yaml
---
id: "top_123"
title: "日记"
attributes_updated_at: "2026-09-07T08:00:00.000Z"
---
```

#### 2. 反向导入时的比对逻辑
当新设备扫描到本地备份文件导入时：
* 如果 DB 里没有该记录 $\rightarrow$ 直接插入。
* 如果 DB 里已有（例如 PowerSync 已经从云端拉了一部分下来） $\rightarrow$ 对比文件里的 `attributes_updated_at` 与数据库里的谁更新，**保留时间戳更新的那一份**。

---

### 三、 联动 PowerSync 时的两大注意事项（重要避坑）

因为你的底层连接着 PowerSync，有两点必须严格处理，否则会导致云端数据被意外覆盖或死循环：

#### 1. 规避反向导入导致“向云端重复上传历史数据”
* **场景**：用户新装 App，此时 DB 是空的。如果在 PowerSync 连上云端之前，App 先读了本地备份文件，把 10,000 条数据作为 `INSERT` 塞进 SQLite，PowerSync 的本地追踪器（Upload Queue）可能会认为：**“用户在本地创建了 10,000 条新记录，我要把它们全部 push 到 Postgres！”**
* **解决方案**：
  * **优先让 PowerSync 建立初始同步（Initial Sync）**：新设备登录时，先等待 PowerSync 完成初始全量下载（从服务端拉取通常秒级完成）。
  * **仅在离线/无账户备份恢复时使用文件导入**：如果必须要用本地文件重建，需确认在无网络或未登录状态下进行，或者查验导入的 ID 是否已经存在，不要无脑触发表的变更追踪。

#### 2. 时间戳生成标准（ISO 8601 vs 毫秒）
* PowerSync 内部通常使用标准 UTC ISO-8601 字符串（例如 `2026-09-07T07:41:00.000Z`）或高精度微秒级时间戳。
* 确保在写文件、比较时间大小时，使用与 PowerSync 统一的格式标准做字符串字典序比对（ISO 8601 支持直接字符串比较大小），避免出现时区不一致导致的“文件时间看似落后于数据库”的 Bug。

### 总结
直接复用 `attributes_updated_at` 是完全正确且专业的做法。你只需要在本地数据库加一个几行代码维护的 `local_file_export_state` 表作为本地高水位标记，就能在不干扰 PowerSync 云同步的前提下，完美实现文件系统的顺向导出与逆向灾备。