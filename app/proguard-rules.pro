# Shizuku uses hidden APIs / reflection.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Keep enum id strings used in SharedPreferences.
-keepclassmembers enum com.mxcfe.sidekey.action.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
