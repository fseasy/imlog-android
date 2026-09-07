---
name: versionCatalogManager
description: Gradle Version Catalog 自动化管理：格式化、检查更新、执行升级
category: build
tags: [gradle, versionCatalog, dependencies]
---

# Version Catalog 管理

## 核心命令

| 命令 | 用途 |
|------|------|
| `./gradlew versionCatalogFormat` | 格式化 TOML 文件 |
| `./gradlew versionCatalogUpdate --check` | 检查依赖更新（只读） |
| `./gradlew versionCatalogUpdate` | 交互式更新 |
| `./gradlew versionCatalogUpdate --all` | 自动更新所有 |

## 执行流程

### 1. 格式化
```bash
./gradlew versionCatalogFormat
```

### 2. 检查更新
```bash
./gradlew versionCatalogUpdate --check
```
输出会标注：
- 🟢 已是最新
- 🟡 有补丁/次要更新
- 🔴 主要版本更新（需谨慎）

### 3. 执行更新

| 场景 | 命令 |
|------|------|
| 逐项确认 | `./gradlew versionCatalogUpdate` |
| 全部自动 | `./gradlew versionCatalogUpdate --all` |
| 仅补丁 | `./gradlew versionCatalogUpdate --patch` |
| 仅次要 | `./gradlew versionCatalogUpdate --minor` |
| 指定依赖 | `./gradlew versionCatalogUpdate --update com.example:lib` |

### 4. 更新后验证
```bash
./gradlew versionCatalogFormat
./gradlew build
./gradlew test
```

## 用户指令模板

- "格式化 version catalog" → 执行任务 1
- "检查依赖更新" → 执行任务 2  
- "更新依赖到最新版本" → 执行任务 3（交互式）
- "自动更新所有依赖" → 执行任务 3（--all）
- "完整维护依赖" → 执行 1→2→3→4

## 版本升级风险

| 版本类型 | 示例 | 风险 | 建议 |
|---------|------|------|------|
| 补丁 | 1.0.0 → 1.0.1 | 低 | 可自动更新 |
| 次要 | 1.0.0 → 1.1.0 | 中 | 审查后更新 |
| 主要 | 1.0.0 → 2.0.0 | 高 | 人工审查，充分测试 |

## 前置条件

项目需应用 `com.github.ben-manes.versions` 插件：

```kotlin
// build.gradle.kts
plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
}
```

## 常见问题

| 问题 | 解决方案 |
|------|---------|
| 命令找不到 | 检查是否应用了 versions 插件 |
| 更新后编译失败 | 回滚，分析变更日志 |
| 网络超时 | 配置国内镜像源 |

---

**版本**：1.0.0
