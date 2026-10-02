# 保留 JSON 序列化相关类（org.json 反射场景兜底）
-keep class com.juge.app.data.WidgetStyle { *; }
-keep class com.juge.app.data.TextShadow { *; }

# 保留 Compose 相关类
-dontwarn androidx.compose.**

# 保留 Timber（调试日志在 Release 中安全）
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}