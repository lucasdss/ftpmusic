# ftpmusic — R8/ProGuard rules (release builds)
#
# The local proxy uses Netty (plain JARs — no consumer rules) for the embedded
# HTTP/1.1 server; keep its reflection/option parsing intact.

# ── Netty (embedded proxy server) ────────────────────────────────────────
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-keep class org.jctools.** { *; }
-dontwarn org.jctools.**
-keep class reactor.blockhound.** { *; }
-dontwarn reactor.blockhound.**
# Netty native transport loader (reflection over tcnative classes)
-keep class io.netty.channel.epoll.** { *; }
-keep class io.netty.channel.kqueue.** { *; }
-keep class io.netty.incubator.channel.uring.** { *; }
-keep class io.netty.internal.tcnative.** { *; }
-dontwarn io.netty.internal.tcnative.**

# ── Log stripping (debug noise must not ship) ────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── Retrofit/Gson models (serialized by Gson reflection) ─────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.lucasdss.ftpmusic.app.data.network.** { *; }
