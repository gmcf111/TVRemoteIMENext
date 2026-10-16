# ijkplayer so JNI 入口
-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class tv.danmaku.ijk.media.player.**.* { *; }
-dontwarn tv.danmaku.ijk.media.**

# NanoHTTPD
-dontwarn org.nanohttpd.**
-keep class org.nanohttpd.** { *; }

# ZXing
-dontwarn com.google.zxing.**
