
1. set in AndroidManifest.xml
   
   ```
   <activity
    ...
    android:windowSoftInputMode="adjustResize"
    ...
   />
   ```

  默认行为是 adjustUnspecified; 系统决定，不同设备可能不同
  Compose 推荐用 adjustResize.