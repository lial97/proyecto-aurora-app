# Reglas de R8 para el APK de release.

# WorkManager crea por reflexión su base de datos de Room, los "InputMerger" y los trabajadores. Glance lo usa
# para dibujar los widgets y atender sus botones: sin estas reglas el widget se redibujaba sin parar con diseños
# distintos y los botones no respondían.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Glance: los botones de los widgets (ActionCallback) y los widgets se crean por su nombre de clase.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
