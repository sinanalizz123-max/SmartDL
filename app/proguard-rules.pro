# Keep Room annotations and generated classes
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# OkHttp and coroutines are proguard-friendly by default; no extra rules needed.
