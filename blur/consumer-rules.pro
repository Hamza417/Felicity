# Consumer ProGuard rules for the blur module.
# Keep the GpuBlur class and its native method so Proguard
# does not rename or strip the JNI bridge method.
-keep class app.simple.felicity.blur.GPUBlur { *; }

