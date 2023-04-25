package io.aibees.knowledgebase

import munit.FunSuite

class ExtractionSuite extends FunSuite {
  test("Phone from text") {
    val examples = Seq(
      "123-456-7890",
      "(123) 456-7890",
      "123 456 7890",
      "123.456.7890",
      "+91 (123) 456-7890",
      "+919367788755",
      "8989829304",
      "+16308520397",
      "786-307-3615",
      "+14155552671",
      "+551155256325"
    )

    examples.foreach(s => assert(Extractors.phone.matches(s)))
  }
}
