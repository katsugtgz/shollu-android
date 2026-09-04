# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# CityRepository parses res/raw/cities.json with Gson reflection into CityEntity.
# R8-renamed fields break the JSON-key <-> field mapping, leaving non-null vals null,
# which fails the Room NOT-NULL insert inside initializeCitiesIfNeeded's runCatching —
# silently yielding an EMPTY cities table (broken city picker) in minified release builds.
-keep class com.ebsoft.shollu.data.db.entity.CityEntity { *; }
# TypeToken<List<CityEntity>> generic signature + reflection plumbing for Gson
-keepattributes Signature, InnerClasses, EnclosingMethod
