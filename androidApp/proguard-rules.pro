# Reglas de R8 para el APK de release.

# WorkManager (lo usan los widgets de Glance) crea su base de datos de Room por reflexión (WorkDatabase_Impl).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
