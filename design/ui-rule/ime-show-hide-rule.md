# 当 IME 打开时，再在消息列表操作时 IME 对应的行为规范

## 其他 App rule
当 IME 弹出的情况下，看了一些 APP 的行为：

## WeChat rule

1. 弹出 context menu 时，不隐藏
2. 滚动消息列表时，隐藏
3. context-menu -> 多选：隐藏键盘

## WhatsApp rule

1. 弹出 context menu （它是在顶部）时，不隐藏
2. 滚动消息列表时，不隐藏
3. 多选时，不隐藏

也就是，二者完全无关！

## DeepSeek

1. 弹出 context menu 时，隐藏 IME！
   注意时序！它是先隐藏 IME 后，再弹出 context menu，这样才能保证 context menu 位置是准确的
2. 滚动消息列表时，隐藏 IME

## 此 App 的 Rule

1. 弹出 context menu 时，仿造 DeepSeek 的逻辑
2. 滚动消息列表时，不隐藏 IME

WHY:

1. 还是认为，弹出 Context Menu 时，用户主要意图是操作消息列表，这时隐藏无关IME 露出更大空间更合理
2. 否则，context menu 很小；一些功能（选择文本）和底部的 IME 打架；多选等需要更大空间