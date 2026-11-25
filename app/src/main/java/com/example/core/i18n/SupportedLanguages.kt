package com.example.core.i18n

data class SupportedLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
)

val SupportedLanguages: List<SupportedLanguage> = listOf(
    SupportedLanguage("en", "English", "English"),
    SupportedLanguage("bn", "বাংলা", "Bengali"),
    SupportedLanguage("hi", "हिन्दी", "Hindi"),
    SupportedLanguage("ur", "اردو", "Urdu"),
    SupportedLanguage("ta", "தமிழ்", "Tamil"),
    SupportedLanguage("te", "తెలుగు", "Telugu"),
    SupportedLanguage("mr", "मराठी", "Marathi"),
    SupportedLanguage("gu", "ગુજરાતી", "Gujarati"),
    SupportedLanguage("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    SupportedLanguage("kn", "ಕನ್ನಡ", "Kannada"),
    SupportedLanguage("ml", "മലയാളം", "Malayalam"),
    SupportedLanguage("zh", "中文", "Chinese"),
    SupportedLanguage("fa", "فارسی", "Persian (Farsi)"),
    SupportedLanguage("ar", "العربية", "Arabic"),
    SupportedLanguage("ne", "नेपाली", "Nepali"),
    SupportedLanguage("id", "Bahasa Indonesia", "Indonesian"),
    SupportedLanguage("es", "Español", "Spanish"),
    SupportedLanguage("fr", "Français", "French"),
    SupportedLanguage("pt", "Português", "Portuguese"),
    SupportedLanguage("tr", "Türkçe", "Turkish")
)

fun findSupportedLanguage(code: String): SupportedLanguage =
    SupportedLanguages.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }
        ?: SupportedLanguages.first()
