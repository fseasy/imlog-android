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
        // 3. 进入页面时，从本地数据库/DataStore 加载历史草稿（如果有）
        viewModelScope.launch {
            val savedDraft = draftRepository.getDraft(topicId)
            if (!savedDraft.isNullOrEmpty() && messageInputText.value.isEmpty()) {
                savedStateHandle["input_text"] = savedDraft
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

