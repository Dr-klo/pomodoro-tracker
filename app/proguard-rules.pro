# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in {sdk.dir}/tools/proguard/proguard-android.txt

# Keep Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Enum constant names in the model package are persisted, so they must survive obfuscation.
# ThemeMode is written to DataStore as `mode.name` and read back by comparing against `it.name`;
# renaming the constants would still be self-consistent on a fresh install, but an update over a
# build that wrote "DARK" would silently fail to match and reset the user's theme to SYSTEM.
# AppLanguage is safe on its own (it stores an explicit `tag`), but the rule covers the package so
# that adding a persisted enum later cannot reintroduce the bug quietly.
-keepclassmembers enum com.drklo.pomodoro.data.model.** { *; }
