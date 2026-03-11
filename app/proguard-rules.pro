# GhostPin ProGuard Rules

# Keep Hilt-generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep data model classes (used reflectively by some serializers)
-keep class com.ghostpin.core.model.** { *; }

# Keep Room database classes — SEC-05
-keep class com.ghostpin.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
