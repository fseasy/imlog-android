## 当用户发送一张图片时，后台流程是怎样的？

当用户选择图片并点击“发送”时，推荐按照以下流程处理：
步骤 A：生成占位消息并立即写入数据库（UI 响应）
这一步在主线程或协程中快速完成，目的是让 UI 立即感知。
生成 UUID 作为临时 msgId。
安全地读取 MediaStore Uri。由于 MediaStore 的 Uri 在应用重启后可能会失去访问权限，建议立即在后台启动处理。
向数据库插入一条状态为 SENDING 的消息。此时 localPath 可以先填入 MediaStore 的 Uri（如果还来不及拷贝），或者在拷贝完成后立即更新。
UI 层（通过 Flow/LiveData 监听数据库）：
检测到新消息插入。
发现 sendStatus 是 SENDING。
使用图片加载库（如 Coil 或 Glide）直接加载本地的 Uri 或临时路径，并在图片上叠加载入动画（ProgressBar）或置灰滤镜。
步骤 B：后台异步处理（拷贝、缩略图、上传）
因为媒体文件处理和网络上传可能耗时，且可能因为应用退到后台而中断，建议使用 Jetpack WorkManager 或 ApplicationScope 的协程 来执行。
拷贝文件到内部存储（沙盒）：
从 MediaStore Uri 读取输入流，写入到应用的内部存储目录（如 context.filesDir/images/）。
原因：MediaStore 权限可能会失效，且用户可能在系统相册中删除该图片。拷贝到内部存储可以保证应用内随时可用。
提取并保存缩略图：
使用 DocumentFile 或 BitmapFactory 解码图片，压缩生成一张小图（例如最大边长 200px-400px）。
将缩略图保存到内部缓存目录（如 context.cacheDir/thumbnails/）。
更新数据库：
将拷贝后的本地原图路径和缩略图路径更新到该消息的 localPath 和 thumbnailPath 中。
此时 UI 会再次刷新，加载库会无缝切换为读取本地沙盒文件。
步骤 C：上传至服务器并更新状态
上传原图（或根据策略上传压缩后的图）到对象存储服务（OSS/S3 等）。
获取服务器返回的 URL。
发送 IM 信令：将消息内容（包含图片 URL、宽、高等元数据）发送给 IM 服务器。
更新数据库：
将 sendStatus 修改为 SUCCESS。
填充 remoteUrl。
此时 UI 监听到状态变为 SUCCESS，隐藏加载动画。


## 是否要等缩略图算好，才开始插入数据库让 UI 显示？

不需要等算好缩略图再插入数据库。如果在插入前进行耗时操作，用户会明显感觉到点击发送后，UI 卡顿或延迟一下才出现气泡。
在实际开发中，我们通常采用 “先用临时数据占位，再异步更新” 的方案。
以下是具体的解决办法：
1. 解决“字段为空”的办法：使用 MediaStore Uri 作为临时路径
   当用户选择图片后，你已经拥有了该图片的 MediaStore Uri（例如 content://media/external/images/media/12345）。这个 Uri 在此时是立即可用的。
   在插入数据库时，你可以直接把这个 Uri 字符串 填入 localPath 字段。

完整的生命周期时间线
   整个过程在极短的时间内发生，对用户来说是完全无感且平滑的：

   时间点	步骤	数据库状态	UI 表现
   0ms	用户点击发送	插入消息：<br>localPath = MediaStore Uri<br>thumbnailPath = null<br>status = SENDING	气泡立刻出现，Coil 开始加载 MediaStore Uri。界面显示转圈动画。
   5ms	UI 渲染完成	保持不变	用户已经看到了要发送的图片。
   10ms - 150ms	后台任务启动 (如 WorkManager)	正在后台：<br>1. 拷贝原图到沙盒<br>2. 生成缩略图到缓存	界面无变化，用户以为正在网络发送。
   150ms	后台任务完成第一阶段	更新数据库：<br>localPath = 沙盒原图路径<br>thumbnailPath = 沙盒缩略图路径	数据库更新，UI 刷新。Coil 自动切换到加载缩略图（因为本地路径已变，且缩略图更小，加载极快，几乎无闪烁）。
   150ms - 1.5s	开始网络上传	保持不变	界面继续显示转圈。
   1.5s	上传成功	更新数据库：<br>remoteUrl = 远端 URL<br>status = SUCCESS	界面转圈动画消失，显示发送成功。
   
## 数据库怎么设计？本地的这些状态不应该被同步吧？

- 本地状态不应该同步：像 local_uri（本地特有的媒体库路径，只对当前手机有效）和 send_status（发送中、上传进度等）是设备特定的（Device-specific）和临时的（Transient）。把它们同步给聊天对手或者你的另一台 iPad，不仅毫无意义，还会浪费网络带宽。
- 只存文件名（Filename）是最佳实践：你目前的 schema 设计中，只存储 filename 和 thumbnail_name，然后在运行时动态拼接绝对路径（如 context.filesDir），这完全符合 Android/iOS 沙盒机制的要求（因为应用更新或系统重构时，沙盒的绝对路径可能会变，只存文件名能保证绝对安全）。
那么，在 PowerSync 架构下，该怎么处理这些本地状态？

- PowerSync 是基于 SQLite 的。对于这类“只需本地感知，无需云端同步”的数据，业界标准的做法是：在本地 SQLite 中建一张“仅限本地（Local-Only）”的扩展表（Extension Table），并且不把它配置进 PowerSync 的同步规则（sync_rules.yaml）中。

1. 设计本地状态表（Local-Only Table）
   在本地数据库中，创建一张专门管理发送/上传状态的表。这张表只存在于当前设备上：
   code
   SQL
   -- 这张表只在本地 SQLite 创建，不要配置到 PowerSync 的云端同步规则中
   CREATE TABLE local_message_states (
   message_id TEXT PRIMARY KEY,    -- 关联 messages.id
   local_uri TEXT,                 -- 选图时的临时 MediaStore Uri
   upload_progress INTEGER DEFAULT 0, -- 上传进度 0-100
   status TEXT NOT NULL            -- 'PENDING_UPLOAD', 'UPLOADING', 'FAILED'
   );
2. 发送图片时的完整工作流
   当用户点击发送图片时，执行以下步骤：
   
   1. 本地原子写入（同一个 SQLite 事务中）：
      插入数据到 messages 表（PowerSync 会自动开始尝试同步这条消息到服务器）。
      插入数据到 local_message_states 表，初始状态为 PENDING_UPLOAD，并记录 local_uri。
      UI 展现（双表联合查询）：
      你的 UI 监听的是一个 LEFT JOIN 的查询：
      ```SQL
      SELECT m.*, l.status, l.local_uri, l.upload_progress
      FROM messages m
      LEFT JOIN local_message_states l ON m.id = l.message_id
      WHERE m.topic_id = :topicId;
      ```
      
   2. UI 渲染逻辑：
          如果 l.status 不为空（说明是自己正在发送的图）：
          图片源直接使用 l.local_uri（瞬间展现）。
          根据 l.status 和 l.upload_progress 显示进度条。
          如果 l.status 为空（说明是别人发过来的图，或者是以前已经发送成功的图）：
          路径拼接：context.filesDir + m.filename（或缩略图）。
          不显示任何上传进度条。
   3. 后台任务（WorkManager / 协程）处理上传：
      拷贝原图到沙盒，生成缩略图。
      更新 local_message_states，把 status 设为 UPLOADING。
      开始上传文件到服务器（如 OSS/S3），期间不断更新 local_message_states.upload_progress（UI 也会实时跟着变）。
      上传成功后：
      更新 messages 表中的 filename 和 thumbnail_name。
      删除 local_message_states 中对应的行（因为它已经不再需要本地状态了）。
      此时，PowerSync 会把 messages 中补全了 filename 的最终版本同步到服务器。
      总结：你的 Schema 需要修改吗？
      messages 表：不需要大改。目前的字段非常干净，适合同步给所有设备。
      新增一张本地表：如上文所述，新增 local_message_states 表用于临时中转，不参与 PowerSync 同步。
      通过这种“本地表 + 同步表 LEFT JOIN”的设计，你既保证了本地 UI 的极致响应速度（Instant UI），又保证了网络同步数据的极简与纯粹。

## 发送消息后的这些操作，写在哪里？ viewModel?

不能写在 ViewModel 里，至少“拷贝、生成缩略图、上传”这些重型操作绝对不能写在 ViewModel 里。
为什么不能写在 ViewModel 里？
ViewModel 的生命周期是与当前页面（Activity/Fragment）绑定的。
如果用户点击发送一张 10MB 的图片，然后立刻点击返回键退出了聊天界面：
当前页面的 ViewModel 会被销毁（onCleared 被调用）。
绑定在 viewModelScope 中的协程会被无情取消。
你的图片拷贝、缩略图生成和网络上传会半途终止。这会导致消息卡在“发送中”状态，甚至产生垃圾文件。
业界的标准架构设计
在 IM 应用中，发送媒体消息通常采用 ViewModel (触发) -> Repository (协调) -> WorkManager (执行) 的分层架构。
各个组件的分工如下：

[ UI (Compose/Activity) ]
│  (用户点击发送)
▼
[ ChatViewModel ]
│  (调用 repo.sendImageMessage)
▼
[ MessageRepository ]
│  (1. 开启事务，向 DB 插入发送中消息)
│  (2. 启动 WorkManager 任务)
▼
[ WorkManager (UploadWorker) ] ──(在系统后台稳定运行，即使应用退出也不影响)
│
├─► 1. 拷贝原图到沙盒
├─► 2. 生成缩略图
├─► 3. 上传图片到服务器
└─► 4. 更新数据库状态为成功

- ViewModel 层：只负责快速响应 UI

- ViewModel 极其轻量，它只负责调用 Repository，然后立即结束，不占用生命周期。
    ```Kotlin
    class ChatViewModel(private val repository: MessageRepository) : ViewModel() {
    
        fun sendImage(imageUri: Uri, topicId: String) {
            viewModelScope.launch {
                // 快速调用，然后界面就可以通过 Livedata/Flow 看到新气泡了
                repository.sendImageMessage(imageUri, topicId)
            }
        }
    }
    ```

- Repository 层：负责数据库操作与启动后台任务
  Repository 运行在应用全局生命周期（或通过 WorkManager），确保即使页面销毁，任务依然被触发。
   
   ```Kotlin
   class MessageRepository(
   private val database: AppDatabase,
   private val workManager: WorkManager
   ) {
   suspend fun sendImageMessage(imageUri: Uri, topicId: String) {
   val messageId = UUID.randomUUID().toString()

        // 1. 立即在事务中插入本地占位数据（让 UI 瞬间展现）
        database.runInTransaction {
            database.messageDao().insert(createPendingMessage(messageId, topicId))
            database.localStateDao().insert(createLocalState(messageId, imageUri))
        }

        // 2. 启动后台 Worker 负责后续重型任务
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadImageWorker>()
            .setInputData(workDataOf(
                "KEY_MESSAGE_ID" to messageId,
                "KEY_URI" to imageUri.toString()
            ))
            // 还可以设置约束，比如：必须有网络时才执行
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()

        workManager.enqueue(uploadWorkRequest)
   }
   }
   ```

  3. WorkManager 层（Worker）：负责所有的重型操作
     WorkManager 是 Google 推荐的用于保证可靠运行的后台任务组件。即便用户退出了应用，甚至系统杀死了进程，系统也会在合适的时候重启 WorkManager 继续完成上传。

     ```Kotlin
     class UploadImageWorker(
     context: Context,
     workerParams: WorkerParameters
     ) : CoroutineWorker(context, workerParams) {

     override suspend fun doWork(): Result {
         val messageId = inputData.getString("KEY_MESSAGE_ID") ?: return Result.failure()
         val uriString = inputData.getString("KEY_URI") ?: return Result.failure()
         val uri = Uri.parse(uriString)

          return withContext(Dispatchers.IO) {
              try {
                  // 1. 拷贝原图到沙盒
                  val originalFile = copyToSandbox(uri, messageId)
                
                  // 2. 生成缩略图
                  val thumbnailFile = generateThumbnail(originalFile, messageId)
                
                  // 3. 更新本地数据库路径（此时 UI 会从加载 Uri 切换为加载本地文件）
                  updateLocalPathsInDb(messageId, originalFile, thumbnailFile)
                
                  // 4. 上传到服务器 (OSS/S3)
                  val remoteUrl = uploadToServer(originalFile) { progress ->
                      // 更新数据库中的进度，UI 进度条会动
                      updateUploadProgressInDb(messageId, progress)
                  }

                  // 5. 成功后：更新 messages.filename，删除 local_message_states
                  finalizeDbStatus(messageId, originalFile.name, thumbnailFile.name, remoteUrl)

                  Result.success()
              } catch (e: Exception) {
                  e.printStackTrace()
                  // 可以更新本地状态为 FAILED，UI 会显示红感叹号
                  markAsFailedInDb(messageId)
                  Result.failure()
              }
          }
     }
     }
     ```
   
总结
   ViewModel：只负责将用户的“发送点击”翻译成对数据层的调用。
   Repository：负责本地数据库的快速写入（确保 UI 立刻有反应），并派发后台任务。
   WorkManager：负责具体的、耗时的、不可靠的 I/O 和网络任务（拷贝、缩略图、上传）。这是 IM 应用保证消息“绝对能发送成功”的黄金标准。

## Worker 

**Expedited Work**

https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#expedited

WorkManager 2.7.0 introduced the concept of expedited work. This allows WorkManager to execute important work while giving the system better control over access to resources.

A potential use case for expedited work might be within a chat app when the user wants to send a message or an attached image. Similarly, an app that handles a payment or subscription flow might also want to use expedited work. This is because those tasks are important to the user, execute quickly in the background, need to begin immediately, and should continue to execute even if the user closes the app

## 写消息的时候，内容怎么暂存？

对于 IM（即时通讯）应用，文本框里的消息文本不能当成普通的输入框处理，它本质上是“消息草稿（Draft）”。

1. 基础版（中小项目/快速实现）：**ViewModel + `SavedStateHandle`**
如果你的 IM 只要求：**聊天时切去别的 App 接个电话，或者应用退后台被系统杀死后返回，文字还在**。

*   **实现方式**：使用 ViewModel + `SavedStateHandle`（绑定当前的 `topicId` 或 `chatId`）。
*   **优点**：实现简单，自动抗进程死亡。
*   **缺点**：如果用户**主动点击返回键退出这个聊天界面**，ViewModel 销毁，未发送的草稿就丢失了。

2. 专业版（主流 IM 标准做法）：**ViewModel + 本地持久化 (Room / DataStore / MMKV)**
真正的 IM 应用（如微信），当你输入到一半**退出聊天界面**回到会话列表时，列表项上会显示标红的 `[草稿] 你好...`。

*   **实现方式**：
    1. 用户在输入框打字时，ViewModel 监听文本变化（加上 Debounce 防抖，比如用户停止打字 500ms 后）。
    2. 将文本作为“草稿”异步保存到本地数据库（如 Room）或 MMKV / DataStore 中，以 `topicId` 为 Key。
    3. 再次进入该聊天框时， ViewModel 从数据库/DataStore 中读取该 `topicId` 的草稿充填输入框。
    4. 点击发送后，清空本地数据库中的草稿。
*   **优点**：
    *   退出聊天界面、甚至主动杀掉 App / 关机重启，草稿都不会丢。
    *   可以在**会话列表**（Chat List）上展示 `[草稿]` 提示。



代码实践示范（专业 IM 架构示例）

ViewModel 应该这样写：

```kotlin
@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository, // 负责本地草稿读写 (Room / DataStore)
    // ... 其他 UseCase
) : ViewModel() {

    // 1. 获取当前聊天的 topicId
    private val route: MainScreen.TopicTimeline = savedStateHandle.toRoute()
    val topicId: TopicId = route.topicId

    // 2. 消息输入框的状态，优先从 SavedStateHandle 读取
    val messageInputText: StateFlow<String> = savedStateHandle.getStateFlow("input_text", "")

    init {
         if (!savedStateHandle.contains("input_text")) {
            // 3. 首次初始化：从 DB 加载（若 SSH 中无数据）
            viewModelScope.launch {
                val savedDraft = draftRepository.getDraft(topicId)
                if (!savedDraft.isNullOrEmpty() && messageInputText.value.isEmpty()) {
                    savedStateHandle["input_text"] = savedDraft
                }
            }
        }

        // 4. 监听文本变化，自动防抖保存草稿到本地数据库
        messageInputText
            .debounce(500) // 用户停止打字 500ms 后才落盘，避免频繁 I/O
            .onEach { text ->
                draftRepository.saveDraft(topicId, text)
            }
            .launchIn(viewModelScope)
    }

    // UI 调用这个函数更新文本
    fun onInputTextChanged(newText: String) {
        savedStateHandle["input_text"] = newText
    }

    // 点击发送消息
    fun sendMessage() {
        val textToSend = messageInputText.value
        if (textToSend.isBlank()) return

        viewModelScope.launch {
            // 发送消息...
            sendMessgeUseCase(topicId, textToSend)
            
            // 发送成功后清空输入框，并删除本地草稿
            savedStateHandle["input_text"] = ""
            draftRepository.clearDraft(topicId)
        }
    }
}
```

### 追问1： 如果我的 App 还有同步功能，且草稿会同步，那这个处理流程是啥呢？这时 savedStateHandle 就不是唯一的真实数据了吧？


### 问题 1：如果草稿支持多端同步，流程是怎样的？`SavedStateHandle` 还是 SSOT 吗？

> 数据的单源真理（SSOT）分层结构

当加入“多端同步”后，**`SavedStateHandle` 确实不再是全局的 SSOT，它退化为“当前本地 UI 会话的独占状态”**。

在支持同步的系统中，SSOT 是**分层**的：
* **全局 SSOT**：云端数据库（Server DB）
* **本地设备 SSOT**：本地数据库（Room / DataStore）
* **UI 临时会话状态**：`SavedStateHandle` / `ViewModel` 内存

处理草稿同步的关键在于 **“区分用户当前是否正在该设备上输入”**，避免远端草稿覆写了用户正在敲字的内容。

```text
[远端 Server] ──(推送/轮询)──> [本地 Room DB]
                                   │
                         (冲突检测: 用户是否在打字?)
                                   │
                       ┌───────────┴───────────┐
                   【正在打字】               【处于静止/初次进入】
                       │                       │
               忽略远端推送(或弹 Toast 提示)   更新 SavedStateHandle & UI
```

> 状态流转规则：

1. **用户本地打字（Local -> Remote）**：
   * 用户打字 -> 立即更新 `SavedStateHandle`。
   * 防抖 500ms 后 -> 写入本地 Room DB。
   * 本地 DB 触发 SyncWorker（或网络请求）-> 同步给云端 Server。
2. **远端草稿更新（Remote -> Local）**：
   * 收到 Web 端/手机 B 端推来的新草稿 -> 写入本地 Room DB。
   * **冲突解决（Conflict Resolution）**：
     * **场景 A（用户未在该页面 / 未焦点）**：本地 DB 改变 -> 更新 `SavedStateHandle` -> UI 自动显示远端最新草稿。
     * **场景 B（用户正在该页面打字，1 秒内有输入动作）**：**本地用户输入优先（Local Wins）**。丢弃远端推送（或在界面顶部提示：“检测到其他设备更新了草稿，[点击查看]”），绝不强制覆盖用户当前正在敲的字！

### 追问 2：另外，怎么确保 viewModel 销毁的时候，draft 数据（inputText）完整保存到了 db 了呢？这个 debounce 可能导致数据不全吧？


要解决这个问题，我们需要针对两种不同的销毁场景分别处理：

> 场景 A：正常退出页面（ViewModel.onCleared 被触发）
当用户主动点击返回键、划走页面时， ViewModel 的 `onCleared()` 会被调用。但此时 `viewModelScope` 已经失效，不能直接在里面发协程。

**正确做法**：注入一个 **`ApplicationScope`（全局应用生命周期的 CoroutineScope）**，在 `onCleared()` 中使用 `NonCancellable` 上下文进行**强制冲刷落盘（Flush）**。

> 场景 B：后台进程被杀（Process Death）
进程被杀时，`onCleared()` **根本不会被执行**，`viewModelScope` 也会瞬间死亡。
**但完全不用担心！** 因为你在打字时，`onInputTextChanged` 已经**同步**写入了 `SavedStateHandle`。
当用户重新打开 App 恢复会话时，`SavedStateHandle` 会直接恢复出最新的字符串，甚至比 DB 还新！


## Topic 的最后一条消息数据，怎么更新

`last_message`（最新消息）本质上是 `messages` 表中**最新一条记录的快照**。它的更新逻辑必须在 **Repository 层（数据仓库层）** 统一管辖。

以下是 **4 个必须更新 `last_message` 的时机**：

1. 收到/发送新消息时（最常见）
无论收到 WebSocket 推送，还是用户点击发送消息成功：
* **动作**：将新消息的`时间`和`格式化后的摘要`写进 `topic_last_message` 表。

> 💡 **摘要（Snippet）生成规则**：
> * 文本消息 $\rightarrow$ 直接截取文本（如 `今天开会`）
> * 图片消息 $\rightarrow$ 存 `[图片]`
> * 语音消息 $\rightarrow$ 存 `[语音]` 或 `[语音 15"]`
> * 撤回消息 $\rightarrow$ 存 `对方撤回了一条消息`

2. 用户撤回/修改消息时
* 如果被撤回/修改的消息**恰好是最后一条消息**：
* **动作**：更新 `last_message_snippet` 为 `"[消息已撤回]"` 或更新后的文本。

3. 用户删除消息时（边界坑点！）
* 如果用户删除了聊天记录中的**最后一条消息**：
* **动作**：你需要去 `messages` 表里重新 `SELECT * FROM messages WHERE topic_id = :id ORDER BY timestamp DESC LIMIT 1` 查询**倒数第二条消息**，并用它更新 `topic_last_message`！
* 如果消息全被清空了，把 `last_message_at` 和 `last_message_snippet` 设为 `NULL`。

4. 首次登录/拉取历史漫游消息时
* 从服务器批量同步完消息后，找到每个 Topic 最新的一条消息，批量更新/插入 `topic_last_message` 表。

---

### 架构层面的代码实现示范 (Repository 层)

在 Android 中，建议在 `MessageRepository` 中写一个私有辅助函数，专门用来同步更新 `topic_last_message`：

```kotlin
class MessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val topicLastMessageDao: TopicLastMessageDao
) {
    // 无论是收到新消息，还是发送新消息，都调用这个函数
    suspend fun saveIncomingOrSentMessage(message: Message) {
        // 1. 存入真实的 messages 表
        messageDao.insert(message)

        // 2. 自动生成摘要
        val snippet = when (message.type) {
            MessageType.TEXT -> message.content
            MessageType.IMAGE -> "[图片]"
            MessageType.AUDIO -> "[语音 ${message.duration}\"]"
            MessageType.FILE -> "[文件] ${message.fileName}"
        }

        // 3. 同步更新 topic_last_message 表（使用 UPSERT：有就更新，没就插入）
        topicLastMessageDao.upsertLastMessage(
            topicId = message.topicId,
            lastMessageAt = message.timestamp,
            lastMessageSnippet = snippet
        )
    }
}
```

总结流程图

```text
 用户打字/选图 ──> 防抖(Debounce) ──> 更新 draft_json & draft_snippet
                                            │
 发送消息按钮 ──> 清空草稿(draft=null) ───┼─> 写入 messages 表
                                            │
                                            └─> 更新 last_message_snippet 
                                                （会话列表收到通知，自动刷新列表展示）
```

## UI 设计

核心想法：记录页面操作一定要简单，不然为啥不用 Notion/Flomo 呢？


首行元素

1. 发送文件 
2. 语音
3. 文本
4. 发送按钮

引用栏：微信里，引用功能是一个独立于输入的功能—任何输入（文本、语音、图片），都可以附带这个引用

整体布局：

｜--- 引用栏 ---      ｜
｜ 功能选择栏 / 输入栏 / 语音录制栏  ｜
｜ 扩展面板栏（输入法栏）｜


初始态：更多+（小）、文本框、语音

点击文件：
    - 顶栏不变 （其他 app 都这样，同时这样操作后，也不用记住它的状态！）
    - 扩展面板展开，显示 + 支持的类型（图片、视频、音频；拍照、摄像）

点击文本框：输入框变大，右边出现「发送」按钮，下面出现 IME；
    - 多行时，高度增加，「发送」保持在底部对齐
    - 焦点取消时，IME 消失（和微信一样），但是整体还是「输入框+发送」的展示，不会出现初始态
    - 删除完文本，才回到初始态
点击语音：
  - 单点：区域变成录制界面
  - 长按：区域变成即时发送的形式
    - 页面渲染动作：向左取消，向上锁定（类似 whatsapp）


## 编辑器工作时，「返回键」的逻辑

目前我根据 whatsapp + 微信逻辑 梳理的如下：
- 如果是在文本编辑状态（text mode, 键盘打开时）：
  - 如果文本框有字，就关闭键盘
  - 如果没字，就回到初始状态
- 如果在 text mode, 但键盘关闭，就返回到前一个页面（navigation pop up）
- 如果是 voice mode,
  - 正在录音：录音暂停、震动、返回到前一个页面
  - 录音已经暂停： 返回前一个页面
- 如果是附件打开状态：关闭附件展开面板，回到初始状态

逻辑怎么写：

**决策交给 ViewModel，执行留在 Page（UI层）**

* **为什么不全部写在 Page？**
  因为 `是否有字`、`录音状态`、`附件面板状态` 都是 ViewModel 持有的状态。如果全在 Page 层写一堆 `if-else`，会导致 View 代码臃肿且无法进行单元测试。
* **为什么不全部写在 ViewModel？**
  因为 `Navigation (popBackStack)`、`HideKeyboard (键盘)`、`Vibration (震动)` 属于 Android 系统级/UI 级的服务，ViewModel **不应该直接持有 NavController 或 Context/Vibrator**。

**最佳实践：** 
1. UI 层（Page）通过 `BackHandler` 拦截返回键，将**当前键盘状态**传给 ViewModel。
2. ViewModel 根据当前状态进行**逻辑判断**，通过 **`SideEffect (一次性事件)`** 发出命令（如 `HideKeyboard`、`Vibrate`、`PopBackStack`）。
3. UI 层监听该事件，执行具体的系统操作。



1. 定义 UI 事件 (Side Effect) 与 State

```kotlin
// ViewModel 发给 UI 层的指令
sealed interface ComposerUiEffect {
    object HideKeyboard : ComposerUiEffect
    object PopBackStack : ComposerUiEffect
    object Vibrate : ComposerUiEffect
}

// 录音状态
enum class RecordingStatus {
    RECORDING, PAUSED, IDLE
}

// 统一的 UI 状态
data class ComposerUiState(
    val text: String = "",
    val composerMode: ComposerMode = ComposerMode.NORMAL, // NORMAL 或 RECORDING
    val currentPanel: InputSelector = InputSelector.NONE, // 附件/表情面板
    val recordingStatus: RecordingStatus = RecordingStatus.IDLE
)
```

2. ViewModel 中的决策逻辑 (`ComposerViewModel.kt`)

把你的 4 条规则原封不动翻译成 ViewModel 的决策代码：

```kotlin
@HiltViewModel
class ComposerViewModel @Inject constructor(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposerUiState())
    val uiState: StateFlow<ComposerUiState> = _uiState.asStateFlow()

    // 发送给 UI 的一次性事件通道
    private val _uiEffect = Channel<ComposerUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    /**
     * 响应返回键点击
     * @param isKeyboardVisible 当前键盘是否处于打开状态（由 UI 层传入）
     */
    fun handleBackPress(isKeyboardVisible: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value

            when {
                // 规则 4: 如果附件/表情面板处于打开状态 -> 关闭面板，恢复初始态
                state.currentPanel != InputSelector.NONE -> {
                    _uiState.update { it.copy(currentPanel = InputSelector.NONE) }
                }

                // 规则 3: 如果处于语音录制模式 (voice mode)
                state.composerMode == ComposerMode.RECORDING -> {
                    if (state.recordingStatus == RecordingStatus.RECORDING) {
                        // 正在录音：暂停录音、触发震动、返回上一页
                        pauseRecordingInternal()
                        _uiEffect.send(ComposerUiEffect.Vibrate)
                    }
                    // 录音已暂停或处理完毕 -> 返回上一页
                    _uiEffect.send(ComposerUiEffect.PopBackStack)
                }

                // 规则 1: 文本编辑状态 + 键盘处于打开状态
                isKeyboardVisible -> {
                    _uiEffect.send(ComposerUiEffect.HideKeyboard)
                    
                    if (state.text.isBlank()) {
                        // 文本框没字 -> 回到初始状态 (失焦、恢复语音胶囊)
                        _uiState.update { 
                            it.copy(
                                composerMode = ComposerMode.NORMAL,
                                currentPanel = InputSelector.NONE
                            ) 
                        }
                    }
                    // 如果有字，只需 HideKeyboard，保持 TextMode 不变
                }

                // 规则 2: Text mode 但键盘关闭 (或初始状态) -> 返回上一页
                else -> {
                    _uiEffect.send(ComposerUiEffect.PopBackStack)
                }
            }
        }
    }

    private fun pauseRecordingInternal() {
        // 执行暂停录音的业务逻辑...
        _uiState.update { it.copy(recordingStatus = RecordingStatus.PAUSED) }
    }
}
```

3. 页面层拦截与执行 (`ChatScreen.kt`)

UI 层使用 Compose 提供的 `BackHandler` 拦截物理返回键/手势，并通过 `LaunchedEffect` 响应 ViewModel 的指令：

```kotlin
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ComposerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // UI 系统服务
    val keyboardController = LocalSoftwareKeyboardController.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    
    // 1. 实时获取当前键盘是否开启
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    // 2. 监听 ViewModel 发出的 UI 指令
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ComposerUiEffect.HideKeyboard -> {
                    keyboardController?.hide()
                }
                is ComposerUiEffect.Vibrate -> {
                    // 触发系统震动反馈
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is ComposerUiEffect.PopBackStack -> {
                    // 离开页面前，确保键盘收起
                    keyboardController?.hide()
                    navController.popBackStack()
                }
            }
        }
    }

    // 3. 拦截系统返回键/侧滑返回手势
    BackHandler(enabled = true) {
        viewModel.handleBackPress(isKeyboardVisible = isKeyboardVisible)
    }

    // 4. 渲染你的 IM Composer 组件
    Scaffold { paddingValues ->
        // ... 聊天消息列表与 UserInput ...
    }
}
```

## 如何让输入框里的展开面板高度，和 IME 的高度一致，防止切换时闪烁

关键：记住 IME 的高度(区分 横竖屏); 正确配置及写好配合的 UI
具体做法：在 UI 层上报 ime 高度给 viewModel，viewModel 记录到 datastore, 然后暴露 state 给 ui 层

记住

ViewModel 层 (ComposerViewModel.kt)

```
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComposerViewModel @Inject constructor(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    // 当前面板应该使用的高度（UI 直接监听此 State）
    private val _panelHeight = MutableStateFlow(280.dp)
    val panelHeight: StateFlow<Dp> = _panelHeight.asStateFlow()

    private var lastIsLandscape: Boolean? = null

    /**
     * 当横竖屏切换时调用，从 Repository 初始化/切换当前高度
     */
    fun onOrientationChanged(isLandscape: Boolean) {
        if (lastIsLandscape == isLandscape) return
        lastIsLandscape = isLandscape

        // 从磁盘读取对应方向的历史高度
        val savedDpValue = repository.getImeHeight(isLandscape)
        val defaultDp = if (isLandscape) 140.dp else 280.dp

        _panelHeight.value = if (savedDpValue > 0f) savedDpValue.dp else defaultDp
    }

    /**
     * 当 UI 层测量到真实的键盘高度时回调
     */
    fun onImeHeightMeasured(isLandscape: Boolean, measuredHeight: Dp) {
        // 键盘未弹出(<=0) 或 高度未发生改变时，不处理
        if (measuredHeight <= 0.dp || measuredHeight == _panelHeight.value) return

        // 1. 更新当前 UI 状态
        _panelHeight.value = measuredHeight

        // 2. 异步持久化到磁盘
        viewModelScope.launch {
            repository.saveImeHeight(isLandscape, measuredHeight.value)
        }
    }
}
```


UI 层变得极其纯粹，只做两件事：
- 测量系统 WindowInsets.ime 并通知 ViewModel；
- 收集 viewModel.panelHeight 并传给扩展面板。

```
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.res.Configuration

@Composable
fun UserInput(
    onMessageSent: (String) -> Unit,
    viewModel: ComposerViewModel = hiltViewModel()
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 1. 感知横竖屏变化，通知 ViewModel 加载对应的历史高度
    LaunchedEffect(isLandscape) {
        viewModel.onOrientationChanged(isLandscape)
    }

    // 2. 实时测量系统的 IME 键盘高度
    val currentImeHeight = with(density) { WindowInsets.ime.getBottom(this).toDp() }

    // 3. 当键盘高度发生变化且大于 0 时，作为 Event 提交给 ViewModel 处理
    LaunchedEffect(currentImeHeight, isLandscape) {
        if (currentImeHeight > 0.dp) {
            viewModel.onImeHeightMeasured(isLandscape, currentImeHeight)
        }
    }

    // 4. 从 ViewModel 收集最终的面板高度 State
    val panelHeight by viewModel.panelHeight.collectAsState()

    // 5. 渲染 UI，传给 SelectorExpanded
    Column {
        // ... 输入框和按钮 ...

        SelectorExpanded(
            currentSelector = currentInputSelector,
            panelHeight = panelHeight, // 使用 ViewModel 算好的高度
            onCloseRequested = { /* ... */ }
        )
    }
}
```



防止抖动的关键配置（重要细节）

如果在切换时依然感到画面有闪烁，请检查以下两点：

1. AndroidManifest.xml 的 WindowSoftInputMode
确保 Activity 配置了 `adjustResize`，这样 Compose 才能准确感知并响应 `WindowInsets.ime` 的变化：
```xml
<activity
    android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize" />
```

2. 在 Activity 中开启 Edge-To-Edge (边到边)
在 `MainActivity.kt` 的 `onCreate` 中开启 `enableEdgeToEdge()`，确保 Compose 能无障碍地读取到最底层的 WindowInsets：
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge() // Android 15+ 默认开启，旧版本需加上
    super.onCreate(savedInstanceState)
    setContent {
        // ...
    }
}
```

示例：

1. 修改扩展面板容器组件

将面板高度设置为动态传入的 `panelHeight`（即 `imeHeight`）：

```kotlin
@Composable
fun SelectorExpanded(
    currentSelector: InputSelector,
    panelHeight: Dp, // <--- 关键：传入 savedImeHeight
    onCloseRequested: () -> Unit,
    onTextAdded: (String) -> Unit
) {
    if (currentSelector == InputSelector.NONE) return

    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight) // <--- 强制面板高度与键盘高度 1:1 完全一致！
    ) {
        when (currentSelector) {
            InputSelector.EMOJI -> EmojiSelector(onTextAdded)
            InputSelector.PICTURE -> PictureSelectorPanel()
            // ...其他面板
            else -> Unit
        }
    }
}
```

2. 整合到 `UserInput` 主逻辑中

```kotlin
@Composable
fun UserInput(
    onMessageSent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentInputSelector by rememberSaveable { mutableStateOf(InputSelector.NONE) }
    
    // 1. 获取记忆的 IME 高度
    val imeHeight = rememberImeHeightOrDefault(defaultHeight = 280.dp)
    
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 监听返回键关闭面板
    if (currentInputSelector != InputSelector.NONE) {
        BackHandler { currentInputSelector = InputSelector.NONE }
    }

    Surface(tonalElevation = 2.dp) {
        Column(modifier = modifier) {
            // 输入框
            UserInputText(
                // ...
                onTextFieldFocused = { focused ->
                    if (focused) {
                        // 输入框获取焦点时，自动关闭扩展面板
                        currentInputSelector = InputSelector.NONE
                    }
                }
            )

            // 底部选择按钮栏（表情、图片等）
            UserInputSelector(
                currentInputSelector = currentInputSelector,
                onSelectorChange = { selector ->
                    if (currentInputSelector == selector) {
                        // 重复点击，切换回键盘
                        currentInputSelector = InputSelector.NONE
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    } else {
                        // 切换到面板：收起键盘，展开面板
                        currentInputSelector = selector
                        keyboardController?.hide() // 先收键盘，面板在下方以相同高度展开，顶栏就不会跳动
                    }
                }
            )

            // 扩展面板（与键盘同高）
            SelectorExpanded(
                currentSelector = currentInputSelector,
                panelHeight = imeHeight, // <--- 注入高度
                onCloseRequested = { currentInputSelector = InputSelector.NONE },
                onTextAdded = { /* ... */ }
            )
        }
    }
}
```


---

总结微信/WhatsApp 的零闪烁秘密：

| 切换方向        | 交互顺序                                                       | 结果                                        |
| :-------------- | :------------------------------------------------------------- | :------------------------------------------ |
| **键盘 ➔ 面板** | 1. 记录键盘高度 $H$<br>2. 展开 $H$ 高度的面板<br>3. 隐藏软键盘 | 界面总高度不变，**完全无闪烁/无跳动**       |
| **面板 ➔ 键盘** | 1. 请求 TextField 焦点拉起键盘<br>2. 键盘弹出的同时关闭面板    | 键盘接管 $H$ 高度的空间，**顶栏稳定无抖动** |


## 如何处理在 composer 外的点击、滚动事件？需要在此时清理掉 composer 的焦点、键盘等

这是一个非常具有工程深度的问题。答案是：**`isScrollInProgress` 确实无法区分手动和自动，而在 IM 业务场景下，你必须将它们区分开。**

如果混淆了这两者，会直接导致一个体验灾难级的 Bug。

---

 一、 为什么必须要区分手动和自动？

假设你不做区分，直接使用 `LaunchedEffect(listState.isScrollInProgress)`：

1. 用户在输入框打字，此时键盘处于弹起状态。
2. 用户点击右侧“发送”按钮。
3. 你的代码逻辑：发送消息 $\rightarrow$ 将新消息追加到列表末尾 $\rightarrow$ 调用 `listState.animateScrollToItem(lastIndex)` **自动滚动到最新消息**。
4. **Bug 触发**：由于自动滚动触发了 `isScrollInProgress = true`，`LaunchedEffect` 瞬间响应，执行了 `focusManager.clearFocus()`。
5. **结果**：**用户每发送一条消息，键盘就会被强行收回一次**。这在 IM 应用里是无法接受的（微信、飞书、QQ 发送消息后，键盘都会保持弹起，以便用户连续输入）。

因此，我们必须**只在用户手动拖拽（Manual Drag）列表时收起键盘**，而在系统由于发送消息、加载历史消息等原因引发自动滚动（Programmatic Scroll）时，保持键盘状态不变。

---

 二、 如何区分手动和自动滚动？

Jetpack Compose 提供了一个专用于追踪用户交互的接口：`InteractionSource`。我们可以通过扩展函数 `collectIsDraggedAsState()` 来**精准捕捉用户的手指拖拽行为**。

* **手动滚动（手指按住并拖拽）**：`isDragged` 为 `true`。
* **自动/程序滚动（如 `animateScrollToItem`）**：`isDragged` 始终为 `false`。

💡 工程级最优解法：

我们将监听条件从 `isScrollInProgress` 替换为 `isDragged`：

```kotlin
import androidx.compose.foundation.interaction.collectIsDraggedAsState

@Composable
fun ChatScreen(
    viewModel: ComposerViewModel = viewModel()
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // 关键：利用 interactionSource 收集用户是否“正在手动拖拽”列表
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    // 仅监听用户的手指拖拽行为
    LaunchedEffect(isDragged) {
        if (isDragged) {
            // 只要用户手指在屏幕上开始拖拽聊天历史：
            focusManager.clearFocus()        // 收起键盘
            viewModel.onInputModeChange(null) // 隐藏附件面板
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                        viewModel.onInputModeChange(null)
                    }
                }
        ) {
            MessageList(
                state = listState, // 绑定列表状态
                modifier = Modifier.fillMaxSize()
            )
        }

        UserInput(...)
    }
}
```

---

 三、 为什么说这是完美的交互体验？

1. **响应极其灵敏**：
   当用户的手指刚刚触碰屏幕并向下拖拽了仅几个像素时，`isDragged` 就会立刻变为 `true`。键盘会在拖拽刚开始的瞬间“丝滑”地收起，没有任何迟滞感。
2. **避开了惯性滑动（Fling）的干扰**：
   当用户快速滑一下屏幕然后松开手，列表会继续惯性滑动。在松手后，`isDragged` 会立刻变回 `false`（即使列表还在滚动）。因为我们在拖拽刚开始时就已经收起了键盘，所以随后的惯性滑动不会引发任何重复执行，保证了性能和稳定性。
3. **完美兼容发消息自动置底**：
   当你点击发送消息，调用 `animateScrollToItem` 滚动列表时，`isDragged` 保持为 `false`，键盘能够稳稳地留在屏幕上，用户可以非常舒服地连续输入。