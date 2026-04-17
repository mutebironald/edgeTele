package com.dimaggi.edgetele.data.model.enums

import java.util.Locale

enum class Language(
    val displayName: String,
    val nativeName: String,
    val assetKey: String,         // key in playbook JSON translations map
    val sttLocale: String,        // BCP-47 locale for Google STT
    val gemmaLocaleHint: String   // hint injected into Gemma prompt
) {
    ENGLISH(
        displayName = "English",
        nativeName = "English",
        assetKey = "en",
        sttLocale = "en-US",
        gemmaLocaleHint = "English"
    ),
    BISLAMA(
        displayName = "Bislama",
        nativeName = "Bislama",
        assetKey = "bi",
        sttLocale = "en-VU",     // fallback: en-US if VU not supported
        gemmaLocaleHint = "Bislama (creole of Vanuatu)"
    ),
    TOK_PISIN(
        displayName = "Tok Pisin",
        nativeName = "Tok Pisin",
        assetKey = "tpi",
        sttLocale = "en-PG",     // fallback: en-AU
        gemmaLocaleHint = "Tok Pisin (creole of Papua New Guinea)"
    ),
    HAITIAN_CREOLE(
        displayName = "Haitian Creole",
        nativeName = "Kreyòl ayisyen",
        assetKey = "ht",
        sttLocale = "ht-HT",     // fallback: fr-HT
        gemmaLocaleHint = "Haitian Creole (Kreyòl ayisyen)"
    );

    fun toLocale(): Locale = Locale.forLanguageTag(sttLocale)

    companion object {
        fun fromAssetKey(key: String): Language =
            entries.firstOrNull { it.assetKey == key } ?: ENGLISH
    }
}
