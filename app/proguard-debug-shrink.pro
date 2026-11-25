# Keep debug builds small (~2.7 MB instead of 29 MB) while preserving readable
# stack traces, line numbers, and full debugger/Logcat inspectability.
-include proguard-rules.pro

-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*
-keepparameternames
