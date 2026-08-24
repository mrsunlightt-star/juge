# 保留 Gson/JSON 序列化相关类
-keep class com.example.myapplication.data.WidgetStyle { *; }
-keep class com.example.myapplication.data.TextShadow { *; }

# 保留 Compose 相关类
-dontwarn androidx.compose.**

# 保留 Timber（调试日志在 Release 中安全）
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}