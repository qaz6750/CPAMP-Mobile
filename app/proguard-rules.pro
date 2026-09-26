# Keep kotlinx.serialization-generated serializers used through Retrofit.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Glance instantiates widget action callbacks reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

