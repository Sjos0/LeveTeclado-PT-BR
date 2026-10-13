# Preservar o motor nativo e métodos JNI
-keep class com.example.keyboard.NativeEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preservar modelos de dados e classes do teclado
-keep class com.example.keyboard.** { *; }

# Otimizações de remoção de código morto e ofuscação
-repackageclasses ""
-allowaccessmodification
