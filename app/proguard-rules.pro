# Library consumer rules (Room, Media3, kotlinx-serialization, OkHttp, WorkManager) cover what this app needs.

# smbj (recordings on a network share): Kerberos (org.ietf.jgss) and mbassador's EL filters are never used on Android.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
# smbj's event bus finds its @Handler methods by reflection.
-keepclassmembers class * { @net.engio.mbassy.listener.Handler *; }
