package com.cyrelis.srag.domain.transcript

import com.cyrelis.srag.domain.error.DomainError
import zio.test.*
import zio.test.Assertion.*

object LanguageCodeSpec extends ZIOSpecDefault:

  override def spec = suite("LanguageCode")(
    test("apply creates valid code successfully") {
      val code = LanguageCode("en")
      assert(code)(isRight(equalTo(LanguageCode.unsafe("en"))))
    },
    test("apply fails for invalid format") {
      val code = LanguageCode("eng")
      assert(code)(isLeft(isSubtype[DomainError.InvalidInput](anything)))
    },
    test("apply fails for null") {
      val code = LanguageCode(null)
      assert(code)(isLeft(isSubtype[DomainError.InvalidInput](anything)))
    },
    test("fromString parses valid code") {
      assert(LanguageCode.fromString("FR"))(isSome(equalTo(LanguageCode.unsafe("fr"))))
    },
    test("allSupportedLanguages list contains expected languages") {
      val all = LanguageCode.allSupportedLanguages
      assert(all)(contains(LanguageCode.unsafe("en"))) &&
      assert(all)(contains(LanguageCode.unsafe("fr")))
    }
  )
