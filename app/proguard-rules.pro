# Lwidget ProGuard / R8 Optimization Rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <init>(...);
}
-keep class com.leanbitlab.lwidget.weather.** { *; }

# Preserve AppWidgetProvider, Receivers, and Services
-keep public class com.leanbitlab.lwidget.AwidgetProvider
-keep public class com.leanbitlab.lwidget.StepCounterService
-keep public class com.leanbitlab.lwidget.SetupActivity
-keep public class com.leanbitlab.lwidget.MainActivity
