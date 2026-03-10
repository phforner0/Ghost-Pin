# GhostPin ProGuard Rules

# Keep Hilt-generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep data model classes (used reflectively by some serializers)
-keep class com.ghostpin.core.model.** { *; }
