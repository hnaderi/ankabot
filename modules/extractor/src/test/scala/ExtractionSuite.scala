package dev.hnaderi.ankabot

import munit.FunSuite
import munit.Location

import extractors.ContactExtractors

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
      assertEquals(ContactExtractors.phonesIn(s).toSeq, Seq(Contact.Phone(s)))
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

    examples.foreach(s =>
      assertEquals(ContactExtractors.phonesIn(s).toSeq, Nil)
    )
  }

  test("Matches emails") {
    val examples = Seq(
      "mail@example.com",
      "john.doe@foo.bar",
      "john.doe@foo.bar.baz.dux",
      "a@b.c"
    )

    examples.foreach(s =>
      assertEquals(ContactExtractors.emailsIn(s), Set(Contact.Email(s)))
    )
  }

  test("Does not match stupid emails") {
    val examples = Seq(
      "",
      "A@P",
      "T@.",
      "bD@o",
      "{@.",
      "5@i4",
      "4$Y@1",
      "c?@Z",
      "c?@Z.z"
    )

    examples.foreach(s =>
      assertEquals(ContactExtractors.emailsIn(s), Set.empty)
    )
  }

  private def Social(kind: SocialNetwork)(
      matching: String*
  )(nonMatching: String*)(using Location) = test(s"Social networks: $kind") {
    matching.foreach(str =>
      assertEquals(
        ContactExtractors.IsSocialNetwork.unapply(str),
        Some(kind),
        str
      )
    )
    nonMatching.foreach(str =>
      assertEquals(
        ContactExtractors.IsSocialNetwork.unapply(str),
        None,
        str
      )
    )
  }

  Social(SocialNetwork.Youtube)(
    "https://www.youtube.com/@pbsspacetime",
    "https://www.youtube.com/@veritasium",
    "https://www.youtube.com/c/veritasium"
  )(
    "https://www.youtube.com/watch?v=123"
  )

  Social(SocialNetwork.Facebook)(
    "https://facebook.com/zuck",
    "https://www.facebook.com/walterknollflorist",
    "https://www.facebook.com/CherryStreetTeriyaki/",
    "https://www.facebook.com/PlatinumSandConstruction",
    "https://www.facebook.com/palmbeachcombathapkido",
    "https://www.facebook.com/designmilk/",
    "https://www.facebook.com/experitravel",
    "https://www.facebook.com/AllianzDirectES",
    "http://www.facebook.com/RedRockRecording",
    "https://www.facebook.com/Paxton-Precast-LLC-219679091564547/",
    "http://www.facebook.com/roadstarraider",
    "https://facebook.com/francinehardaway",
    "https://www.facebook.com/rondarampageent/",
    "https://www.facebook.com/229397857105660",
    "https://www.facebook.com/DIEAUTOSEITEN/",
    "https://www.facebook.com/LaDecoreriaSofas/",
    "https://www.facebook.com/brazukacoffee/",
    "https://www.facebook.com/iocasia",
    "https://www.facebook.com/johannavoytishandren",
    "https://www.facebook.com/warnerbrosent/"
  )(
    "https://www.facebook.com/policies?ref=pf",
    "https://www.facebook.com/privacy/center/?entry_point=facebook_page_footer",
    "https://www.facebook.com/pages/create/?ref_type=site_footer",
    "https://www.facebook.com/privacy/policy/?entry_point=facebook_page_footer",
    "https://www.facebook.com/policies/cookies/",
    "https://www.facebook.com/login/?next=https%3A%2F%2Fwww.facebook.com%2FHotelT%2F#",
    "https://www.facebook.com/ad_campaign/landing.php?placement=pflo&campaign_id=402047449186&nav_source=unknown&extra_1=auto",
    "https://www.facebook.com/help/568137493302217",
    "https://www.facebook.com/login/help/637205020878504",
    "https://www.facebook.com/r.php?next=https%3A%2F%2Fwww.facebook.com%2FHotelT%2F&locale=en_US&display=page",
    "https://www.facebook.com/recover/initiate/?ars=facebook_login",
    "https://www.facebook.com/allactivity?privacy_source=activity_log_top_menu",
    "https://www.facebook.com/biz/directory/",
    "https://www.facebook.com/votinginformationcenter/?entry_point=c2l0ZQ%3D%3D",
    "https://www.facebook.com/help/?ref=pf"
  )

  Social(SocialNetwork.Linkedin)(
    "http://www.linkedin.com/in/johndoe"
  )()

  Social(SocialNetwork.LinkedinCompany)(
    "http://www.linkedin.com/company/google"
  )()

  Social(SocialNetwork.Github)(
    "https://github.com/hnaderi/"
  )(
    "https://github.com/hnaderi/ankabot"
  )
  Social(SocialNetwork.Gitlab)(
    "https://gitlab.com/hnaderi/"
  )(
    "https://gitlab.com/abc/def"
  )
  Social(SocialNetwork.Instagram)(
    "https://instagram.com/apple",
    "https://www.instagram.com/bcbhomes/",
    "https://www.instagram.com/riversagency/",
    "https://www.instagram.com/thrivechurchtx/",
    "https://www.instagram.com/fabandt/",
    "https://www.instagram.com/summitcharterschool/",
    "https://www.instagram.com/thinkmotive/",
    "https://www.instagram.com/mikschrealty/",
    "https://www.instagram.com/3cprovisions/"
  )(
    "https://www.instagram.com/explore/locations/262536400/oakbrook-preparatory-school",
    "https://www.instagram.com/reel/Chx2lR4jRWb/?igshid=YmMyMTA2M2Y%3D",
    "https://www.instagram.com/p/CFR6OATle5y/",
    "https://www.instagram.com/p/CFhxag7F3EM/",
    "https://www.instagram.com/p/CFc-qoiFuJ8/",
    "https://www.instagram.com/p/CFkLiCol_cn/",
    "https://www.instagram.com/p/B9CfrW7p0nz/",
    "https://www.instagram.com/p/B8ZBT5zlK2n/",
    "https://www.instagram.com/p/B_koW8tlvyA/"
  )
  Social(SocialNetwork.Pinterest)(
    "https://www.pinterest.com/popwoodworking/"
  )()

  Social(SocialNetwork.Twitter)(
    "https://twitter.com/riversagency",
    "https://twitter.com/thrivechurchtx?lang=en",
    "https://twitter.com/fabandt",
    "https://twitter.com/thinkmotive",
    "https://twitter.com/tweetHarkness/"
  )(
    "http://twitter.com/home?status=717+Teal+Court+https%3A%2F%2Fwww.bcbhomes.com%2Fproperties%2F717-teal-court-2",
    "http://twitter.com/home?status=Silver+King+Model+%26%238211%3B+Boca+Grande%2C+FL+https%3A%2F%2Fwww.bcbhomes.com%2Fproperties%2Fsilver-king-model-boca-grande-fl",
    "http://twitter.com/home?status=1076+Nelson%26%238217%3Bs+Walk+https%3A%2F%2Fwww.bcbhomes.com%2Fnews%2Fproperties%2F1076-nelsons-walk-2",
    "http://twitter.com/home?status=701+Hollybriar+Lane+https%3A%2F%2Fwww.bcbhomes.com%2Fproperties%2F701-hollybriar-lane",
    "http://twitter.com/share?text=Home&url=https%3A%2F%2Fwww.larocheguesthouse.com%2F"
  )

  Social(SocialNetwork.Twitch)(
    "https://www.twitch.tv/ninja",
    "https://www.twitch.tv/shroud",
    "https://www.twitch.tv/valkyrae",
    "https://www.twitch.tv/drdisrespect",
    "https://www.twitch.tv/lirik",
    "https://www.twitch.tv/pokimane",
    "https://www.twitch.tv/timthetatman",
    "https://www.twitch.tv/summit1g",
    "https://www.twitch.tv/amouranth",
    "https://www.twitch.tv/asmongold"
  )(
  )
  Social(SocialNetwork.TikTok)(
    "https://www.tiktok.com/@charlidamelio",
    "https://www.tiktok.com/@addisonre",
    "https://www.tiktok.com/@zachking",
    "https://www.tiktok.com/@justmaiko",
    "https://www.tiktok.com/@spencerx",
    "https://www.tiktok.com/@bellapoarch",
    "https://www.tiktok.com/@daviddobrik",
    "https://www.tiktok.com/@babyariel",
    "https://www.tiktok.com/@jasonderulo",
    "https://www.tiktok.com/@zoe.laverne"
  )(
    "https://www.tiktok.com/explore",
    "https://www.tiktok.com/music",
    "https://www.tiktok.com/discover",
    "https://www.tiktok.com/privacy-policy",
    "https://www.tiktok.com/trending",
    "https://www.tiktok.com/shop",
    "https://www.tiktok.com/about",
    "https://www.tiktok.com/help",
    "https://www.tiktok.com/terms-of-service",
    "https://www.tiktok.com/blog"
  )
  Social(SocialNetwork.Medium)(
    "https://medium.com/@hnaderi.dev"
  )(
    "https://medium.com/@hnaderi.dev/event-driven-fractals-44f59a55ff9"
  )
  Social(SocialNetwork.Crunchbase)(
    "https://www.crunchbase.com/organization/google"
  )()
}
