
### 一、 最直观的方法：使用 Android Studio 依赖分析器（GUI）

1. 在 Android Studio 右侧边栏打开 **Gradle** 工具窗口。
2. 找到你的模块（通常是 `:app`），右键点击它。
3. 选择 **Analyze Dependencies...**。
4. 在弹出的 Dependency Analyzer 窗口中，在上方搜索框输入 **`okio`**。
5. 选中搜索出的 `okio`，右侧或下方会直接展示一条完整的引用树链条，例如：
   ```text
   io.coil-kt:coil-compose:2.6.0
      └─ io.coil-kt:coil-base:...
          └─ com.squareup.okio:okio:3.x.x
   ```

---

### 二、 最快速的方法：使用 Gradle 命令行精确追踪（`dependencyInsight`）

打开 Android Studio 底部终端（Terminal），运行以下命令：

```bash
# 如果是常规 Android app 模块
./gradlew :app:dependencyInsight --dependency okio

# 如果是多平台 Compose Multiplatform (CMP) 模块，通常是 :composeApp
./gradlew :composeApp:dependencyInsight --dependency okio
```

输出的格式是**自底向上**展示的依赖路径，例如：
```text
com.squareup.okio:okio:3.6.0
\--- com.airbnb.android:lottie:6.4.0
     \--- com.airbnb.android:lottie-compose:6.4.0
          \--- compileClasspath
```
这样你一眼就能看出是哪个依赖把你引入了 `okio`。

---

### 三、 查看整个模块的完整依赖树

如果你想看 Jetpack Compose 到底拉取了哪些库：

```bash
# 输出到控制台
./gradlew :app:dependencies --configuration debugRuntimeClasspath

# 或者输出到文本文件方便全局搜索
./gradlew :app:dependencies > dependencies.txt
```
打开 `dependencies.txt` 搜索 `compose` 或 `okio`，可以纵览整个依赖关系图谱。

---

### 四、 常见“顺带引入 Okio”的 Compose 周边库

如果你的项目里引入了以下库，那么出现 Okio 是完全正常的：

1. **图片加载库（最常见）**
   - **Coil** (`io.coil-kt:coil-compose` 或 Coil 3)：Coil 的底层磁盘缓存、字节流处理全面基于 Okio。
2. **动画库**
   - **Lottie Compose** (`com.airbnb.android:lottie-compose`)：Lottie 解压 `.lottie` 压缩包和解析 json 流时使用了 Okio。
3. **网络与序列化**
   - **Retrofit / OkHttp**：OkHttp 强依赖 Okio。
   - **Moshi**：Square 出品的 JSON 解析库底层也是 Okio。
4. **数据存储**
   - **Jetpack DataStore**：在新版本或跨平台（KMP）版本中，引入了 `androidx.datastore:datastore-core-okio`。

**总结**：只要没有引起依赖版本冲突（Duplicate class 等编译错误），传递进来的 Okio 不需要刻意移除，它是上层库正常运行所依赖的基础 I/O 支撑组件。