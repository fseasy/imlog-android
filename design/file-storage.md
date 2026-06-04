## 3 个层次

1. db -> 默认就在 /data 的内部存储里，暂时不用管
2. 缩略图 -> 放到 外部私有存储！这些卸载就删除；读写更方便
3. 用户的消息数据 -> 放到外部共享存储；卸载不删除；
   
   有 2 个选择：一个是用 SAF （Storage Access Framework）先弹出一个窗口让用户选择路径，
   然后用这个路径作为主目录。选择后就无需权限。
   生成的路径都是 Uri，且必须保存下来！不然要想基于相对路径去拼 Uri 的话，需要逐层调用 .find, 效率很低

   还有一个选择就是 Media Store. 它无需用户选择；读写效率稍高，<del>但同样受限于 Uri</del> -> DS 又说存一个 Id 或者直接 query 就很快。
   无需用户选择的代价就是，文件只能放到固定的几个公有目录下，<del>最合适的就是 Documents/$APP_NAME 了。</del>

   https://developer.android.com/training/data-storage/shared/media 翻看官方文档，
   没有看到说可以用 Documents. 感觉还是算了。

   按官方的来，就用 SAF 吧。其实读取外部共享存储的时间并不多啊。
   
   