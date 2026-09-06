-keepattributes *Annotation*

# CameraX selects its backend and device workarounds through providers and
# class-name lookups. Keep the camera runtime intact in optimized releases.
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# ML Kit discovers its barcode components through manifest metadata and
# Firebase component registrars. The bundled scanner also calls native code.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
