# jcifs-ng uses reflection and optional bouncycastle/logging deps.
-dontwarn org.slf4j.**
-dontwarn org.bouncycastle.**
-dontwarn jcifs.**
-keep class jcifs.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
