
### 录音编码选择

录音期间：ADTS-LC, 每帧都有时间同步头，抗崩溃，方便 append segment

发送期间：转为 .m4a 容器，无需编码流，只是换封装格式；通用性极好

ogg? 如果录音时没选 opus，就不建议用; 因为 ADTS -> ogg，会解码又编码，其实带来了损失；同时这个过程也比较慢。

## VoiceRecorder 在哪里创建？

- 在 ViewModel 里创建 VoiceRecorder 合适吗？
    对于即时通讯（IM）这种短语音录制（通常限制在 60 秒以内），在 ViewModel 中创建和管理 VoiceRecorder 是非常合适且推荐的。
    为什么合适？
    生命周期同步：ViewModel 的生命周期与 UI 界面（Activity/Fragment）绑定。当用户正在录音时，如果发生屏幕旋转（Configuration Change），ViewModel 不会被销毁，录音和计时器能够继续正常工作。
    便于清理：当用户退出当前聊天界面，ViewModel 会执行 onCleared()，我们可以在这里安全地调用 VoiceRecorder.release()，确保不发生内存泄漏或硬体资源占用。
    响应式数据流：ViewModel 可以直接将 VoiceRecorder 的 state 和 elapsedMs 暴露给 Compose 或 XML 视图，实现 UI 的实时刷新。
    注意事项
  - 上下文（Context）传递：
  VoiceRecorder.start() 需要传入 Context。请不要把 Activity 的 Context 传给 ViewModel 长期持有，这会导致内存泄漏。
  做法：在 start() 方法被触发时，从界面层传入 context.applicationContext，或者在 ViewModel 中通过 Hilt 注入 Application 上下文。
  - 场景限制：
    - 如果你的业务是“录音笔”、“通话录音”这类长时录音应用，用户期望把 App 退到后台、甚至锁屏时还能继续录音，那么仅仅放在 ViewModel 里就不够了，因为一旦系统内存紧张，后台的 Activity 和 ViewModel 随时可能被系统回收。这种场景就必须使用前台服务（Foreground Service）。

 
- 什么是前台服务（Foreground Service）？
   在 Android 中，Service（服务） 是一个可以在后台执行长时间运行操作的组件，它没有用户界面。
   而 Foreground Service（前台服务） 是一种特殊的服务：
   高优先级：它被系统认为拥有较高的优先级，几乎不会在系统内存不足时被强制杀死。
   用户知情：为了防止应用在后台偷偷消耗电量或侵犯隐私，系统规定前台服务必须在系统的通知栏显示一个常驻的（不可滑动删除的）通知。用户一眼就能看到“这个应用正在后台运行”。
   权限声明：在 Android 11 (API 30) 及以上版本，如果前台服务需要使用麦克风，必须显式声明其类型为 microphone。

## Voice Composer 状态转移


### 从 Recorder Action 的角度

对应 start, stop, cancel.

#### Start recorder action

> idle status, start from start-holding action

| start status | action       | final state              |
| ------------ | ------------ | ------------------------ |
| success      | start native | holding-active-recording |
| fail         | tips         | idle                     |

> idle status, start from start-direct-locked action

| start status | action       | final state             |
| ------------ | ------------ | ----------------------- |
| success      | start native | locked-active-recording |
| fail         | tips         | idle                    |

> committedDraft status, start from resume-locked action

| start status | action       | final state             |
| ------------ | ------------ | ----------------------- |
| success      | start native | locked-active-recording |
| fail         | tips         | committedDraft          |

特别注意：

在 success/fail 内，用户/callback 可能已经调了 stop/cancel, 状态被设置为 *-on-race, 需要检查当前状态并调用对应行为。

#### STOP recorder action 

> hold status, stop from send-action [done]

| stop status | action                      | final state |
| ----------- | --------------------------- | ----------- |
| on-rase     | commit-draft(fail), tips    | idle        |
| success     | commit-draft(success), send | idle        |
| fail        | commit-draft(fail), tips    | idle        |

`on-race` 的行为其实和 `fail` 完全一样，区别只在：

1. 把执行的时机，从 instant, 变为了 once start-done.
2. 错误原因上，可以固定写「时间太短」 
 
=> 实现上，我们底层直接调用 Cancel, 并指定对应的的 final state

> locked-base status, stop from send-action [done]

除了调用的 coordinator 行为不同，状态转移和 `hold status, stop from send-action` 完全一致（因为 voice-draft 都是 base 一样）

see `hold status, stop from send-action`.

> locked-base status, stop from pause-action/callback [done]

| stop status | action                   | final state    |
| ----------- | ------------------------ | -------------- |
| on-rase     | commit-draft(fail), tips | idle           |
| success     | commit-draft(success)    | committedDraft |
| fail        | commit-draft(fail), tips | idle           |


> locked-appending status, stop from send-action [done]

| stop status | action                      | final state    |
| ----------- | --------------------------- | -------------- |
| on-race     | commit-draft(fail), tips    | committedDraft |
| success     | commit-draft(success), send | idle           |
| fail        | commit-draft(fail), tips    | committedDraft |

> locked-appending status, stop from pause-action/callback [done]

| stop status | action                   | final state    |
| ----------- | ------------------------ | -------------- |
| on-race     | commit-draft(fail), tips | committedDraft |
| success     | commit-draft(success)    | committedDraft |
| fail        | commit-draft(fail), tips | committedDraft |

> hold -> hold-to-locked, stop from send-action/pause-action/callback

在 stop 时刻必然是 `locked-base status`, 所以 see `locked-base status` 对应内容


### 从 UI 行为的角度

行为角度看对应的状态流转，覆盖上述 recorder 角度看不到的状态。前面覆盖的状态不再重复。

#### 在 committedDraft 状态

> committedDraft status, ui resume-action [done]

action: resume

then: start.


> committedDraft status, ui send-action [done]

action: send

then based on the send result:

| ui action result | action                | final state |
| ---------------- | --------------------- | ----------- |
| success          | -                     | idle        |
| fail             | - (can't do anything) | idle        |


## Voice Draft 的数据库读取、校验以及 UI 层面提示

### Write Flow

VoiceComposerStateHolder: call save


domain(TopicRepository): call save

data-layer: 
- domain model to data entity model
  - 涉及到 waveform pack to 5bit

db-layer: 
- entity to json
- write json-str to table column

### Load Flow

db-layer: 
- read out json str
- json to entity 
  - 容错：json 解析出错
  - 返回 Result<EntityData>

data-layer: 
  - parse entity to domain-model
    - 容错：unpack 5bit to waveform 出错
    - 返回 LoadedVoiceDraft
      - None
      - JsonDeserializeError
      - WaveformUnpackError
      - Success

domain(TopicRepository): return

usecase:
- 校验 LoadedVoiceDraft
  - None -> Pass
  - JsonDeserializeError -> Tips: invalid data
  - WaveformUnpackError -> then same as Success (leaves loaded waveform = null)
  - Success ->
    - base audio verify (quick) [if has]
    - pending audio scan (remove unfinished rear part) [if has]
    - build CommittedDraft
      - rebuild Waveform (base waveform is optional)
      - update duration

VoiceComposerStateHolder: load.

1. 发现 audio broken 后，会把 audio 放到 quarantine 文件夹内，文件名和 draft 文件名相同
2. 合并文件的时候，并非直接 append，而是写到 temp 文件，再建立新的 CommittedDrat, 最后移除旧文件

