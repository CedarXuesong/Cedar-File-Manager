-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**
-keepclasseswithmembernames class * {
    native <methods>;
}
