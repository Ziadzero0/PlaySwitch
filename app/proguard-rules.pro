# Play Switch ProGuard rules
-keep class com.playswitch.ziadzero.PlaySwitchApp { *; }
-keep class com.playswitch.ziadzero.IRootAccountService** { *; }
-keepclassmembers class * extends com.playswitch.ziadzero.IRootAccountService$Stub { *; }
-keep class com.playswitch.ziadzero.root.RootAccountService { *; }
-keep class com.playswitch.ziadzero.ui.MainViewModel {
    public <init>(android.app.Application);
}
-keep class com.topjohnwu.superuser.** { *; }
-keepattributes SourceFile,LineNumberTable
