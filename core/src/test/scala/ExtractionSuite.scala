package io.aibees.knowledgebase

import munit.FunSuite

class ExtractionSuite extends FunSuite {
  test("Matches Phone numbers from text") {
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

    examples.foreach(s =>
      assertEquals(Extractors.phonesIn(s).toSeq, Seq(Contact.Phone(s)))
    )
  }

  test("Does not matches dates as phone numbers from text") {
    val examples = Seq(
      "2023",
      "2014 20.30",
      "2018-2022",
      "2003-2022",
      "2023-23946",
      "2022-08-14",
      "22/04/2023",
      "2023/04/22",
      "22-04-2023",
      "22.04.2023",
      "22/04/23",
      "22-04-23",
      "22 04 23"
    )

    examples.foreach(s => assertEquals(Extractors.phonesIn(s).toSeq, Nil))
  }
}
