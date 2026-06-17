# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in {sdk.dir}/tools/proguard/proguard-android.txt

# Keep Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
