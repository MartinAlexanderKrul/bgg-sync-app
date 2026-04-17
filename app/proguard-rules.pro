# Keep BGG and Google API model classes
-keep class com.bgg.combined.model.** { *; }
-keep class com.google.api.** { *; }
-keep class com.google.android.gms.** { *; }

# Moshi / Retrofit
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jackson (used by Google API client)
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Google HTTP client
-dontwarn com.google.api.client.**
-dontwarn com.google.http.client.**

# ZXing
-keep class com.google.zxing.** { *; }
