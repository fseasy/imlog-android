---
name: sqlDelightMigration
description: |
  SQLDelight 数据库迁移配置与验证 Skill。
  用于自动配置迁移校验、生成基准 schema、验证迁移脚本正确性。
  适用于 Android/KMP 项目中使用 SQLDelight 的场景。
category: database
tags: [sqlDelight, migration, gradle, kmp, android]
version: 1.0.0
author: fseasy+deepseek
---

# SQLDelight 数据库迁移自动化 Skill

## 概述

本 Skill 用于自动化 SQLDelight 数据库迁移的配置和验证流程，确保迁移脚本的正确性，防止有问题的迁移逻辑发布上线。

**核心能力**：
- 自动配置 Gradle 迁移校验参数
- 生成基准数据库 schema 文件
- 验证迁移脚本的正确性
- 在编译期阻断错误的迁移

---

## 使用场景

### 何时使用此 Skill

1. 项目首次引入 SQLDelight 迁移校验
2. 需要修改数据库表结构（添加/删除/修改字段）
3. 团队需要规范数据库迁移流程
4. CI/CD 中需要自动化验证迁移脚本

### 前置条件

- 项目已使用 SQLDelight
- 项目使用 Gradle Kotlin DSL (build.gradle.kts)
- 已定义至少一个数据库版本

---

## 任务执行流程

### 任务 1: 配置 Gradle 迁移校验

**目标**: 在 `build.gradle.kts` 中添加迁移校验配置

**执行步骤**:

1. 检查并确认项目中的数据库名称（通常是 `SqlDelightDb` 或自定义名称）
2. 在 `sqldelight` 闭包中的对应数据库配置块添加：
   - `schemaOutputDirectory` - 指定 schema 文件输出目录
   - `verifyMigrations` - 开启迁移校验

3. 自动检测项目类型选择合适的路径：
   - KMP 跨端项目 → `src/commonMain/sqldelight/databases`
   - Android 单端项目 → `src/main/sqldelight/databases`

**配置示例**:

```kotlin
sqldelight {
  databases {
    create("SqlDelightDb") {
      packageName.set("${libs.versions.appPackageName.get()}.sqldelight")

      // 指定生成的 .db schema 文件存放目录
      schemaOutputDirectory.set(file("src/main/sqldelight/databases"))

      // 开启构建时自动校验 migration 文件
      verifyMigrations.set(true)
    }
  }
}
```

### 任务 2: 生成基准 Schema 文件

**目标**: 为当前数据库版本生成基准 `.db` 文件

**执行步骤**:

1. 确认当前数据库版本号（例如当前是版本 1）
2. 确保 `.sq` 文件保持旧版本的表结构
3. 运行 Gradle 任务生成 schema：

```bash
# 通用命令
./gradlew generateDebugSqlDelightSchema

# 指定具体数据库
./gradlew generateDebugSqlDelightDbSchema
```

4. 确认当前版本的 db 文件生成在 `schemaOutputDirectory` 配置的目录下
5. 将新生成的 db 文件通过 git 提交到仓库

### 任务 3: 创建迁移脚本

**目标**: 编写从旧版本到新版本的迁移 SQL 脚本

**执行步骤**:

1. 更新 `.sq` 文件为新版本的表结构
2. 在 `sqldelight` 源码目录下创建迁移脚本文件
3. 命名规范：`<版本号>.sqm` 或 `<起始版本>_<目标版本>.sqm`
4. 写入对应的迁移 SQL 语句（如 `ALTER TABLE`、`CREATE TABLE` 等）

**命名示例**:
- `1.sqm` - 从版本 1 升级到版本 2
- `1_2.sqm` - 明确标识从版本 1 到版本 2

### 任务 4: 验证迁移脚本

**目标**: 验证迁移脚本的正确性

**执行步骤**:

1. 运行迁移验证任务：

```bash
# 通用命令
./gradlew verifyDebugSqlDelightMigration

# 指定具体数据库
./gradlew verifyDebugSqlDelightDbMigration
```

2. 检查执行结果：
   - ✅ BUILD SUCCESSFUL - 迁移脚本正确
   - ❌ BUILD FAILED - 迁移脚本有问题，查看错误信息修复

3. 常见错误类型：
   - 拼写错误
   - 数据类型不匹配
   - 缺少字段
   - 索引/约束定义错误

---

## 自动化决策逻辑

### 路径选择逻辑

```
IF 项目存在 "commonMain" 目录 (KMP项目) THEN
    schemaOutputDirectory = "src/commonMain/sqldelight/databases"
ELSE
    schemaOutputDirectory = "src/main/sqldelight/databases"
```

### 版本号检测逻辑

```
# 扫描 sqldelight 源码目录
# 查找现有的 .db 文件，确定当前最高版本
# 查找现有的 .sqm 文件，确定已执行的迁移
```

### 迁移脚本命名规则

```
# 如果存在 .db 文件
最高版本号 = MAX(从 .db 文件提取的版本号)
新脚本名称 = "${最高版本号}.sqm"

# 如果不存在 .db 文件 (首次迁移)
新脚本名称 = "1.sqm"
```

---

## 交互式指令模板

### 指令 1: 配置迁移校验

```
请执行 SQLDelight 迁移配置任务：
1. 在 build.gradle.kts 的 sqldelight 配置中添加 schemaOutputDirectory 和 verifyMigrations
2. 使用路径：[src/main/sqldelight/databases 或 src/commonMain/sqldelight/databases]
3. 数据库名称：[SqlDelightDb 或自定义名称]
```

### 指令 2: 生成基准 Schema

```
请执行 SQLDelight 基准 Schema 生成任务：
1. 当前数据库版本：[版本号]
2. 运行 generateSqlDelightDbSchema 任务
3. 确认 `[版本号].db` 文件位置在 [schemaOutputDirectory] 目录下,
```
### 指令 3: 创建迁移脚本

```
请执行 SQLDelight 迁移脚本创建任务：
1. 源版本：[版本号]
2. 目标版本：[版本号]
3. 表结构变更：[具体变更描述]
4. 生成迁移脚本，包含以下 SQL 语句：
   [粘贴 SQL 语句]
```

### 指令 4: 验证迁移

```
请执行 SQLDelight 迁移验证任务：
1. 运行 verifySqlDelightDbMigration 任务
2. 分析执行结果
3. 如果失败，根据错误信息修复迁移脚本
4. 重新验证直到成功
```

---

## 完整工作流示例

### 场景：从版本 1 迁移到版本 2

**用户输入**：
```
我需要将 SQLDelight 数据库从版本 1 迁移到版本 2。
变更内容：删除 last_read_at 字段，新增 last_read_message_id 字段。
```

**AI 执行流程**：

1. **检查配置** (任务 1)
   - 检查 build.gradle.kts 是否有迁移配置
   - 如果没有，添加配置

2. **生成基准文件** (任务 2)
   ```bash
   ./gradlew generateSqlDelightDbSchema
   ```

3. **修改表结构** (任务 3)
   - 更新 .sq 文件，移除 last_read_at，添加 last_read_message_id
   - 创建迁移脚本 `1.sqm`:
   ```sql
   -- 创建新表
   CREATE TABLE channel_read_new (
       channel_id TEXT NOT NULL,
       last_read_message_id INTEGER NOT NULL
   );

   -- 复制数据
   INSERT INTO channel_read_new (channel_id, last_read_message_id)
   SELECT channel_id, last_read_message_id FROM channel_read;

   -- 删除旧表
   DROP TABLE channel_read;

   -- 重命名新表
   ALTER TABLE channel_read_new RENAME TO channel_read;
   ```

4. **验证迁移** (任务 4)
   ```bash
   ./gradlew verifySqlDelightDbMigration
   ```
   - 如果失败，分析错误并修复
   - 如果成功，完成迁移

---

## 输出报告模板

### 迁移完成报告

```
📊 SQLDelight 迁移报告
═══════════════════════════════════════

✅ 迁移状态：成功

📁 配置信息：
  - 数据库名称：SqlDelightDb
  - Schema 目录：src/main/sqldelight/databases
  - 校验开关：已启用

📦 版本信息：
  - 源版本：1
  - 目标版本：2
  - 迁移文件：1.sqm

📝 变更内容：
  - 删除字段：last_read_at
  - 新增字段：last_read_message_id

✅ 验证结果：
  - 迁移脚本语法正确
  - 表结构匹配
  - 通过校验

📌 后续操作：
  1. 提交代码到版本控制
  2. 在 CI/CD 中确保校验任务执行
  3. 通知团队成员迁移已完成
```

---

## 故障排查指南

### 常见问题及解决方案

| 问题 | 可能原因 | 解决方案 |
|------|---------|----------|
| verifyMigrations 任务失败 | 迁移脚本语法错误 | 检查 SQL 语法，确保与目标表结构匹配 |
| 找不到 .db 文件 | 未生成基准文件 | 运行 generateSqlDelightDbSchema 生成 |
| 版本号不匹配 | .sqm 命名错误 | 确保 .sqm 文件命名为正确的版本号 |
| 表结构不一致 | 迁移脚本逻辑错误 | 检查迁移步骤，确保最终表结构匹配 .sq 文件 |
| KMP 项目路径错误 | 路径指向 main 而非 commonMain | 修改 schemaOutputDirectory 为 commonMain 路径 |

---

## 注意事项

1. **版本管理**：确保 `.db` 文件和 `.sqm` 文件的版本号连续且正确
2. **数据完整性**：迁移脚本中如果有数据迁移，务必备份或验证数据完整性
3. **回滚方案**：建议准备回滚脚本，以防迁移出现问题
4. **CI/CD 集成**：在 CI/CD 流程中加入 `verifySqlDelightMigration` 任务
5. **团队协作**：迁移前通知团队成员，避免并发修改导致冲突
6. **测试覆盖**：迁移后务必进行充分的功能测试

---

## 相关资源

- [SQLDelight 官方文档 - Migrations](https://cashapp.github.io/sqldelight/migrations/)
- [SQLDelight Gradle 配置文档](https://cashapp.github.io/sqldelight/multiplatform/gradle/)
- [SQLite ALTER TABLE 语法](https://www.sqlite.org/lang_altertable.html)

---

## 版本历史

- v1.0.0 (2026-09-07): 初始版本，包含基础迁移配置和验证流程
