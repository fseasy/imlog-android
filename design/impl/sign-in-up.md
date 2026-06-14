我有一个 local first 的 android jetpack kotlin app,  现在要写登录注册页面 SignInUp
我的目前想法是这样的：
1. 因为是纯粹的 local first, 我现在不想设置密码，因为我不想让用户再记一个密码，增加用户使用成本，而且我还没有密码找回的功能；后续增加订阅功能，会有一个 online 账户，这时再引入密码。可以吗？另外，可以调用系统自带的密码验证逻辑吗（如手势啥的），但这是不是应该在应用有自己密码之后？
2. 在 SignInUp，UI 第一页应该是同时保留注册/登录的功能；登录部分，需要检查当前是否有登录用户 （基于 app_init_data 表），如果有就列出来，没有就是登录的按钮
3. 然后在用户选择了登录、注册按钮之后，再跳转到对应的页面，这里“跳转”应该怎么做？基于 NavHost  吗？
请帮我思考下，基于最佳实践

---

基于 AI 的结论：

1. Local first 不设置密码是很正常的, 可以变成 “个人档案/profile“
2. 可以调用 bio 验证，但没必要在启动阶段，直接在设置页面中，提供选项开启即可
3. 是应该用 Jetpack Navigation (NavHost)

---

整理下进入到注册登录页可能的状态：

1. 无任何登录用户 -> CreateUser
2. 有 1...N 个 -> SelectUser (带一个创建新用户的按钮跳转)

所以有如下的 screen

1. CreateUser [基于是否有登录用户，确定是否显示使用 xxx user 登录]
2. SelectUser [带 create user 按钮]

