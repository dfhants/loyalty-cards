# WorkManager (used by Glance widgets) builds its Room database reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# ML Kit discovers its components reflectively (ComponentRegistrar classes named in the manifest).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

# WorkManager (which runs Glance's widget sessions) creates these by reflection; without their
# constructors the widgets never render and the launcher drops them.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class androidx.glance.session.** { *; }
-keep class androidx.glance.appwidget.** { *; }
-keep class androidx.glance.** { *; }
-keep class androidx.compose.remote.** { *; }
-dontwarn androidx.compose.remote.**
