# Rules for the release build, where R8 shrinks and renames everything it can
# prove is unreachable or private. What follows is what it cannot prove.
#
# Components declared in the manifest — MainActivity, InCallActivity,
# PhoneCallService, the receivers — are kept by the Android plugin itself, so
# they are not repeated here.

# ---------------------------------------------------------------------- traces
# A crash from a tester has to be readable. Line numbers stay; the file name is
# flattened so nothing of the source tree leaks with them.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------ Room
# Room looks its generated implementation up by name: databaseBuilder receives
# AppDatabase::class.java and Room asks for "AppDatabase_Impl" through
# Class.forName. Renamed, it is never found, and the application dies on the
# first database access — which here is the first screen.
-keep class com.example.data.AppDatabase { *; }
-keep class com.example.data.AppDatabase_Impl { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Database class * { *; }
# The entities and their DAOs are reached from that generated code.
-keep class com.example.data.model.** { *; }
-keep interface com.example.data.*Dao { *; }

# ---------------------------------------------------------------------- ML Kit
# The text recogniser loads its pieces dynamically, through Play Services. This
# is the scanner of the application: if it is stripped, SCAN stops doing the one
# thing it is named after.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ------------------------------------------------------------------- telephony
# Read through the telecom and telephony binders by name on some manufacturers.
-dontwarn android.telecom.**
-dontwarn android.telephony.**
