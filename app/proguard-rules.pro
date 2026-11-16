# A Media3, a Room és a DataStore saját consumer-szabályokat szállít.

# JAudioTagger: az ID3-kereteket és -mezőket név alapján, reflexióval példányosítja
# (pl. "org.jaudiotagger.tag.id3.framebody.FrameBody" + azonosító), ezért a teljes könyvtár marad.
-keep class org.jaudiotagger.** { *; }
# Az asztali Java képkezelése (AWT, ImageIO) Androidon nincs; az app ezeket az utakat nem hívja.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
