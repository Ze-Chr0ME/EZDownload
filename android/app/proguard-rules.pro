# NewPipe evaluates YouTube's player JavaScript using Rhino, with no native runtime.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-keep class org.mp4parser.** { *; }
-keep class org.aspectj.** { *; }
-dontwarn java.lang.management.**
# Desktop-only JPEG track builder is not used for MP4 video/audio remuxing.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
# NewPipe loads timeago patterns by reflection.
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
# Protobuf-lite looks up generated message fields by name.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
# Preserve Rhino's JavaScript engine integration.
-keep class org.mozilla.javascript.engine.** { *; }
# Rhino's optional Java-to-JSON converters are not required on Android.
-dontwarn org.mozilla.javascript.JavaToJSONConverters
