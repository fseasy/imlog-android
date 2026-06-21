
### 录音编码选择

Opus vs. AAC：核心特性对比

特性	Opus	AAC
设计目标	实时通信 (VoIP, 视频会议)	文件存储与分发 (本地音乐, 流媒体)
码率范围	6 kbps - 510 kbps (动态范围极宽)	8 kbps - 529 kbps
典型延迟	26.5 ms (最低可至 5 ms)	> 100 ms
抗丢包	内置强大 (FEC, PLC 等)	依赖外部实现，表现较弱
网络适应性	卓越 (可根据网络质量无缝、动态调整)	一般
专利授权	无，完全免费	有，专利池复杂，商业使用需授权
Web标准	WebRTC 强制必选	非必须

📝 作为 Android 开发者，该如何选择？

- 如果你的 App 核心功能是实时语音/视频通话，或是面向全球用户的语音消息应用，那么 Opus 是唯一的最优解。

  挑战：实现复杂。需要在 native 层集成 libopus 库，编写 JNI 代码进行编解码处理。

  收益：提供顶级、稳定的跨区域通话体验，这是顶尖应用的基石。

- 如果你的 App 主要是一个简单的、不涉及实时聊天的本地录音功能，比如发送一条语音反馈、备忘录录音等。

  结论：那么 MediaRecorder 直接录制 AAC (M4A) 的方案依然是最高效、最简单的选择。

  理由：开发成本极低，原生支持稳定可靠，兼容性没有死角。文件体积和质量也能满足绝大多数非极端场景的需求。

所以，对于通用场景，AAC 是务实之选；而要在 IM 通信领域追求极致，转向 Opus 是必经之路。



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