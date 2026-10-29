package io.github.smyrna_pergamum.osintbook.web

import kotlinx.serialization.json.*

/** Interface translations: English text is the key, and static/i18n/<language>.json maps it to each language.
 *  The same dictionaries translate reports generated on the server. */
object I18n {
    val languages = linkedMapOf("en" to "English","tr" to "Türkçe","de" to "Deutsch","fr" to "Français","es" to "Español",
        "it" to "Italiano","pt" to "Português","ru" to "Русский","ar" to "العربية")
    private val dictionaries: Map<String, Map<String, String>> = languages.keys.associateWith { lang ->
        val text = I18n::class.java.getResourceAsStream("/static/i18n/$lang.json")?.bufferedReader()?.use { it.readText() }
        text?.let { Json.parseToJsonElement(it).jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content } }.orEmpty()
    }
    fun of(code: String?) = code?.takeIf { it in languages } ?: "en"
    fun t(lang: String, key: String) = dictionaries[of(lang)]?.get(key) ?: dictionaries["en"]?.get(key) ?: key

    /** Browser script: language from the user's choice, then the browser language, then English. Arabic is right-to-left. */
    val script: String by lazy {
        val langs = JsonObject(languages.mapValues { JsonPrimitive(it.value) })
        val dicts = JsonObject(dictionaries.mapValues { (_, d) -> JsonObject(d.mapValues { JsonPrimitive(it.value) }) })
        """'use strict';
const languages=$langs;
const dictionaries=$dicts;
const language=(()=>{try{const s=localStorage.getItem('osint-language');if(s&&languages[s])return s}catch{}const n=(navigator.language||'en').slice(0,2).toLowerCase();return languages[n]?n:'en'})();
document.documentElement.lang=language;document.documentElement.dir=language==='ar'?'rtl':'ltr';
function tr(key){return dictionaries[language]?.[key] ?? dictionaries.en?.[key] ?? key;}
/** Replaces {name} placeholders after translation. */
function trf(key,values){return tr(key).replace(/\{(\w+)\}/g,(m,n)=>values[n]??m);}
document.title='OSINT-Book · '+tr(document.title.includes('Admin')?'Admin panel':'Research workspace');
"""
    }
}
