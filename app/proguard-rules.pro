# ── 공통 속성 유지 ──────────────────────────────────────────────
# 제네릭 시그니처(Retrofit 반환 타입)·애노테이션(Moshi/Retrofit)·내부 클래스 정보 유지.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Kotlin 메타데이터(리플렉션 기반 Moshi KotlinJsonAdapterFactory가 사용) 유지
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }

# ── Moshi ──────────────────────────────────────────────────────
-keep class com.koretide.app.data.remote.dto.** { *; }
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}
# @JsonClass(generateAdapter=true) 로 생성된 어댑터 유지
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
# 리플렉션 어댑터가 참조하는 DTO 생성자 파라미터 유지
-keepclassmembers class com.koretide.app.data.remote.dto.** {
    <init>(...);
}

# ── Retrofit 2.9.0 (자체 consumer 규칙 미포함 → 직접 추가) ────────
# @GET/@POST 등이 붙은 서비스 인터페이스 메서드 유지
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation interface * extends <1>

# R8 full mode: Retrofit 이 리플렉션으로 참조하는 타입 유지
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# 프로젝트의 KHOA/KASI/OdCloud 서비스 인터페이스는 그대로 유지
-keep interface com.koretide.app.data.remote.** { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ── OkHttp / Okio ──────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
