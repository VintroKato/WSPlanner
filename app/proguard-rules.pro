# General attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Models & Enums
-keep class com.vintro.wsplanner.models.** { *; }
-keep class com.vintro.wsplanner.enums.** { *; }

# ViewBinding
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}
