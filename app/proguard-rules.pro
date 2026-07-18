# Keep Kotlin Coroutines classes and methods to prevent NoSuchMethodError on channels
-keep class kotlinx.coroutines.** { *; }

# Keep LiteRT-LM classes and native JNI interfaces
-keep class com.google.ai.edge.litertlm.** { *; }
