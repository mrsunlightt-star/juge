# 保留 JSON 序列化相关类（org.json 反射场景兜底）
-keep class com.juge.app.data.WidgetStyle { *; }
-keep class com.juge.app.data.TextShadow { *; }

# WidgetStyle 的 shape / font / bgImageScaleMode / textureType 等枚举，
# 序列化走 Enum.name()、反序列化走 valueOf()（见 WidgetStyle.safeEnum / fromJsonString）。
# R8 的 enum unboxing 会把这些枚举压成 int，name() 字符串随之消失——
# 编译期不会有任何提示，但用户桌面上已保存的组件样式会全部读不回来。
# 保留 values()/valueOf() 可以阻止 unboxing，是这条字符串协议的兜底。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Compose 相关类
-dontwarn androidx.compose.**

# 保留 Timber（调试日志在 Release 中安全）
# 注意：上面这条 assumenosideeffects 会把 Timber 的调用**整条删掉**，所以 Timber 写的日志
# 在 release 包里永远看不到。真机诊断要用 android.util.Log.d（见下方规则），不要用 Timber。
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# 保留 android.util.Log.d / Log.i：真机诊断日志的唯一可用通道。
# R8 默认会剥掉 Log 调用，release 包里完全无输出，导致"打了日志却查不到"的问题。
-keepclassmembers class android.util.Log {
    public static int d(java.lang.String, java.lang.String);
    public static int i(java.lang.String, java.lang.String);
}
