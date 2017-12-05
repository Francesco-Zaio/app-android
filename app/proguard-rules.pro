# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\SO000228\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# keep external libraries classes
-keep class com.agamatrix.** { *; }
-dontwarn com.agamatrix.**
-keep class com.creative.** { *; }
-dontwarn com.creative.**
-keep class com.ftdi.** { *; }
-dontwarn com.ftdi.**
-keep class com.ihealth.** { *; }
-dontwarn com.ihealth.**
-keep class serial.jni.** { *; }
-dontwarn serial.jni.**
-keep class tw.com.prolific.driver.** { *; }
-dontwarn tw.com.prolific.driver.**
-keep class com.example.smartlinklib.** { *; }
-dontwarn com.example.smartlinklib.**
-keep class com.example.gltest.** { *; }
-dontwarn com.example.gltest.**
-keep class com.hoho.android.** { *; }
-dontwarn com.hoho.android.**
