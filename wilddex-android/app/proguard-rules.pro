# LiteRT / TensorFlow Lite uses JNI and reflection.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class io.github.krixhnarr.wilddex.** { kotlinx.serialization.KSerializer serializer(...); }
