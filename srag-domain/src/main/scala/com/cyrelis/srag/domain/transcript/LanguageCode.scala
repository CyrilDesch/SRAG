package com.cyrelis.srag.domain.transcript

import scala.util.matching.Regex

import com.cyrelis.srag.domain.error.DomainError

/** ISO 639-1 language code (2-letter code) */
opaque type LanguageCode = String

object LanguageCode:
  private val iso6391Pattern: Regex = "^[a-z]{2}$".r

  def apply(code: String): Either[DomainError, LanguageCode] =
    if (isValid(code)) Right(code.toLowerCase)
    else Left(DomainError.InvalidInput(s"Invalid ISO 639-1 language code: $code"))

  def unsafe(code: String): LanguageCode = code.toLowerCase

  def isValid(code: String): Boolean =
    code != null && iso6391Pattern.matches(code.toLowerCase)

  def fromString(code: String): Option[LanguageCode] =
    if (isValid(code)) Some(code.toLowerCase) else None

  extension (lang: LanguageCode) def value: String = lang

  def allSupportedLanguages: List[LanguageCode] = List(
    English,
    French,
    Spanish,
    German,
    Italian,
    Portuguese,
    Russian,
    Dutch,
    Polish,
    Ukrainian,
    Czech,
    Slovak,
    Hungarian,
    Romanian,
    Bulgarian,
    Croatian,
    Serbian,
    Slovenian,
    Macedonian,
    Bosnian,
    Albanian,
    Greek,
    Turkish,
    Finnish,
    Swedish,
    Norwegian,
    Danish,
    Icelandic,
    Estonian,
    Latvian,
    Lithuanian,
    Catalan,
    Galician,
    Basque,
    Welsh,
    Irish,
    Breton,
    Maltese,
    Luxembourgish,
    Faroese,
    Chinese,
    Japanese,
    Korean,
    Hindi,
    Bengali,
    Tamil,
    Telugu,
    Marathi,
    Gujarati,
    Kannada,
    Malayalam,
    Punjabi,
    Oriya,
    Assamese,
    Nepali,
    Sinhala,
    Sindhi,
    Urdu,
    Thai,
    Vietnamese,
    Indonesian,
    Malay,
    Tagalog,
    Javanese,
    Sundanese,
    Burmese,
    Khmer,
    Lao,
    Mongolian,
    Kazakh,
    Kyrgyz,
    Uzbek,
    Tajik,
    Turkmen,
    Uyghur,
    Tibetan,
    Dzongkha,
    Arabic,
    Persian,
    Pashto,
    Kurdish,
    Hebrew,
    Amharic,
    Tigrinya,
    Somali,
    Hausa,
    Yoruba,
    Igbo,
    Swahili,
    Zulu,
    Xhosa,
    Afrikaans,
    Shona,
    Tsonga,
    Malagasy,
    Maori,
    Samoan,
    Tongan,
    Fijian,
    Tahitian,
    Armenian,
    Georgian,
    Azerbaijani,
    Bashkir,
    Tatar,
    Chuvash,
    Yiddish,
    HaitianCreole,
    Latin
  )

  val English: LanguageCode       = "en"
  val French: LanguageCode        = "fr"
  val Spanish: LanguageCode       = "es"
  val German: LanguageCode        = "de"
  val Italian: LanguageCode       = "it"
  val Portuguese: LanguageCode    = "pt"
  val Russian: LanguageCode       = "ru"
  val Dutch: LanguageCode         = "nl"
  val Polish: LanguageCode        = "pl"
  val Ukrainian: LanguageCode     = "uk"
  val Czech: LanguageCode         = "cs"
  val Slovak: LanguageCode        = "sk"
  val Hungarian: LanguageCode     = "hu"
  val Romanian: LanguageCode      = "ro"
  val Bulgarian: LanguageCode     = "bg"
  val Croatian: LanguageCode      = "hr"
  val Serbian: LanguageCode       = "sr"
  val Slovenian: LanguageCode     = "sl"
  val Macedonian: LanguageCode    = "mk"
  val Bosnian: LanguageCode       = "bs"
  val Albanian: LanguageCode      = "sq"
  val Greek: LanguageCode         = "el"
  val Turkish: LanguageCode       = "tr"
  val Finnish: LanguageCode       = "fi"
  val Swedish: LanguageCode       = "sv"
  val Norwegian: LanguageCode     = "no"
  val Danish: LanguageCode        = "da"
  val Icelandic: LanguageCode     = "is"
  val Estonian: LanguageCode      = "et"
  val Latvian: LanguageCode       = "lv"
  val Lithuanian: LanguageCode    = "lt"
  val Catalan: LanguageCode       = "ca"
  val Galician: LanguageCode      = "gl"
  val Basque: LanguageCode        = "eu"
  val Welsh: LanguageCode         = "cy"
  val Irish: LanguageCode         = "ga"
  val Breton: LanguageCode        = "br"
  val Maltese: LanguageCode       = "mt"
  val Luxembourgish: LanguageCode = "lb"
  val Faroese: LanguageCode       = "fo"

  val Chinese: LanguageCode    = "zh"
  val Japanese: LanguageCode   = "ja"
  val Korean: LanguageCode     = "ko"
  val Hindi: LanguageCode      = "hi"
  val Bengali: LanguageCode    = "bn"
  val Tamil: LanguageCode      = "ta"
  val Telugu: LanguageCode     = "te"
  val Marathi: LanguageCode    = "mr"
  val Gujarati: LanguageCode   = "gu"
  val Kannada: LanguageCode    = "kn"
  val Malayalam: LanguageCode  = "ml"
  val Punjabi: LanguageCode    = "pa"
  val Oriya: LanguageCode      = "or"
  val Assamese: LanguageCode   = "as"
  val Nepali: LanguageCode     = "ne"
  val Sinhala: LanguageCode    = "si"
  val Sindhi: LanguageCode     = "sd"
  val Urdu: LanguageCode       = "ur"
  val Thai: LanguageCode       = "th"
  val Vietnamese: LanguageCode = "vi"
  val Indonesian: LanguageCode = "id"
  val Malay: LanguageCode      = "ms"
  val Tagalog: LanguageCode    = "tl"
  val Javanese: LanguageCode   = "jv"
  val Sundanese: LanguageCode  = "su"
  val Burmese: LanguageCode    = "my"
  val Khmer: LanguageCode      = "km"
  val Lao: LanguageCode        = "lo"
  val Mongolian: LanguageCode  = "mn"
  val Kazakh: LanguageCode     = "kk"
  val Kyrgyz: LanguageCode     = "ky"
  val Uzbek: LanguageCode      = "uz"
  val Tajik: LanguageCode      = "tg"
  val Turkmen: LanguageCode    = "tk"
  val Uyghur: LanguageCode     = "ug"
  val Tibetan: LanguageCode    = "bo"
  val Dzongkha: LanguageCode   = "dz"

  val Arabic: LanguageCode    = "ar"
  val Persian: LanguageCode   = "fa"
  val Pashto: LanguageCode    = "ps"
  val Kurdish: LanguageCode   = "ku"
  val Hebrew: LanguageCode    = "he"
  val Amharic: LanguageCode   = "am"
  val Tigrinya: LanguageCode  = "ti"
  val Somali: LanguageCode    = "so"
  val Hausa: LanguageCode     = "ha"
  val Yoruba: LanguageCode    = "yo"
  val Igbo: LanguageCode      = "ig"
  val Swahili: LanguageCode   = "sw"
  val Zulu: LanguageCode      = "zu"
  val Xhosa: LanguageCode     = "xh"
  val Afrikaans: LanguageCode = "af"
  val Shona: LanguageCode     = "sn"
  val Tsonga: LanguageCode    = "ts"
  val Malagasy: LanguageCode  = "mg"

  val Maori: LanguageCode    = "mi"
  val Samoan: LanguageCode   = "sm"
  val Tongan: LanguageCode   = "to"
  val Fijian: LanguageCode   = "fj"
  val Tahitian: LanguageCode = "ty"

  val Armenian: LanguageCode      = "hy"
  val Georgian: LanguageCode      = "ka"
  val Azerbaijani: LanguageCode   = "az"
  val Bashkir: LanguageCode       = "ba"
  val Tatar: LanguageCode         = "tt"
  val Chuvash: LanguageCode       = "cv"
  val Yiddish: LanguageCode       = "yi"
  val HaitianCreole: LanguageCode = "ht"
  val Latin: LanguageCode         = "la"
