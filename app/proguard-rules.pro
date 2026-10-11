# thunder JNI 参数类：字段布局不可改动（JNI 依赖）
-keep class com.xunlei.downloadlib.** { *; }

# ijkplayer so JNI 入口
-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class tv.danmaku.ijk.media.player.**.* { *; }
-dontwarn tv.danmaku.ijk.media.**

# NanoHTTPD
-dontwarn org.nanohttpd.**
-keep class org.nanohttpd.** { *; }

# ZXing
-dontwarn com.google.zxing.**
