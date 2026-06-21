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