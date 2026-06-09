
## 数据库字段删除: Hard or Soft

客户端本地数据库（SQLDelight）：强烈推荐 【Hard Delete（物理删除）】

- 手机存储空间极度宝贵：如果用户注销了账号，或者删除了某个用户，你必须把该用户相关的图片、缓存、历史消息全部清理干净。逻辑删除（只改个标志位）会导致垃圾数据永久占用用户的手机空间，时间久了 App 就会变成“存储空间杀手”。

- 极简的级联删除（Cascade）： 在 SQLite 中，你只需开启外键约束，设置 ON DELETE CASCADE。删除了 user 表中的行，该用户的所有消息、状态、群成员记录会被数据库底层自动一并抹去。

服务端数据库：通常推荐 【Soft Delete（逻辑删除）/ 匿名化】

逻辑删除的痛点：
- 查询污染：你几乎所有的查询都必须带上 WHERE is_deleted = 0。
- 联合查询变复杂：查消息时，还得额外 Join 检查发送者是否已被删除。
- 唯一索引冲突：如果 username 是唯一的。用户 A 删除了账号（逻辑删除，记录还在），新用户 B 想用同一个 username 注册，就会因为唯一索引冲突而失败。

服务端删除的最佳实践：【匿名化（Anonymization）】

为了遵守隐私法律（如 GDPR 要求“被遗忘权”），同时又不破坏数据库的关联完整性，服务器端目前最流行的是**“匿名化物理删除”**：
- 当用户选择“注销账号”时，服务器执行一个事务：
    - 将该用户的 username、avatar_uri、email 等所有能识别个人身份的信息（PII）抹去，替换为 Ghost、已注销用户 或随机无意义字符。
    - 将该用户的 is_active 状态设为 false。
    - 保留主键 id 以及他们发过的消息。

- 效果：数据库关联没有断（不需要级联删除海量消息），但用户的个人隐私数据已经被物理擦除了。

特定场景的处理：

1. 用户注销：这个时候直接往服务器发送阻塞请求，待服务器处理完后，本地直接删除数据（数据库+Preference)
2. 服务器通知 xxx 资源已被删除：本地硬删除
3. 本地删除了某个东西：先软删除，同步给服务器；服务器完成同步后，本地再硬删除

代码注意：SQLite 级联删除需要开启

```Kotlin
val driver = AndroidSqliteDriver(
    schema = SqlDelightDb.Schema,
    context = context,
    name = "imlog.db",
    // Enable foreign_keys to enable cascade delete
    callback = object : AndroidSqliteDriver.Callback(SqlDelightDb.Schema) {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Enable foreign keys
            db.setForeignKeyConstraintsEnabled(true)
        }
    }
)
```

## 当创建者注销账户时，该怎么处理？

不能级联删除！

“创建者”是一个历史既定事实（谁创建了它）。如果创建者注销了，这个话题还在，只是“创建者不见了”（变成了匿名/神秘人）。
我们可以去掉 NOT NULL 约束，并将删除策略改为 ON DELETE SET NULL：
1. 修改后的 SQL：
   code
   SQL
   CREATE TABLE topics (
   id TEXT NOT NULL PRIMARY KEY,
   name TEXT NOT NULL,
   icon_uri TEXT,
   -- 👈 去掉了 NOT NULL，改成了 ON DELETE SET NULL
   creator_id TEXT REFERENCES users(id) ON DELETE SET NULL,
   created_at INTEGER NOT NULL,
   attributes_updated_at INTEGER NOT NULL,
   is_deleted INTEGER NOT NULL DEFAULT 0
   );
2. 这种设计下，移交权限和注销的运行流程：
   初始化：用户 Creator 创建了 Topic。此时 topics.creator_id = 'Creator'。
   移交权限：Creator 将 topic_members 表中用户 A 的 role 修改为 'ADMIN'。
   Creator 注销账户：
   数据库执行 DELETE FROM users WHERE id = 'Creator'。
   魔法发生：topics.creator_id 被自动设为了 NULL（话题保留，只是没有创建者了）。
   用户 A 依然存在于 topic_members 中，且 role 仍为 'ADMIN'，话题得以继续被 A 完美管理。
   (注意：在 Kotlin 中，你的 Topic 实体类中的 creatorId 就需要声明为可空类型 TopicId?)。

Q：所有关联用户都删除了，那这个 Topic 不就成孤儿了吗？
A: 你提的这个场景非常现实：假设一个 Topic 有 3 个成员，他们陆续都注销了账户，此时 topic_members 表里关于这个 Topic 的记录变成了 0，而 topics 表里依然残留着这条话题记录。
在关系型数据库中，外键级联（Cascade）只能“自上而下”（删父表，自动删子表），无法“自下而上”（删光了子表，自动删父表）。
最佳解决方案：SQLite 触发器（Trigger）
在本地 SQLite 中，解决孤儿数据最优雅、最省心的方法是写一个触发器。让数据库在 topic_members 发生删除时，自动检查并清理掉没有成员的 Topic。
你可以在 SQLDelight 的 .sq 文件底部加上这个触发器：

```SQL
-- 当 topic_members 表发生删除后，自动执行
CREATE TRIGGER IF NOT EXISTS delete_orphaned_topics
AFTER DELETE ON topic_members
BEGIN
-- 删除那些在成员表里已经没有任何人关联的 topics
DELETE FROM topics
WHERE id = old.topic_id
AND NOT EXISTS (
SELECT 1 FROM topic_members WHERE topic_id = old.topic_id
);
END;
```
PS: 注意是 `old`, `OLD` 就不行； old 是数据库触发器（Trigger）机制中的一个特殊伪行（Pseudo-row）变量。这个 SQL 只有在 SQLite 里才有效

有了这个触发器，你完全不需要在 Kotlin 中写任何清理逻辑，数据库会自动保证绝对没有孤儿 Topic。
