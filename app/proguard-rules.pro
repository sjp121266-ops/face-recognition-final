# Keep TensorFlow Lite model-facing classes straightforward for the course build.
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# ML Kit and CameraX are SDK-facing integration points in this project. Keeping
# their public adapters stable makes the release build safer for final delivery.
-keep class com.google.mlkit.** { *; }
-keep class androidx.camera.** { *; }
