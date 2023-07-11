package dev.hnaderi.ankabot
package extractors

import cats.syntax.all.*

object ContactExtractors {
  val links: DataExtractor = in =>
    ExtractedData(
      contacts = in.page.links.collect { case IsContact(value) => value }
    )

  object IsContact {
    private val emailPattern = """^mailto:(.+@.+)""".r
    private val phonePattern = """^tel:(.{8,})""".r
    private val invalidPhoneCharacters = "[^+0-9]".r

    def unapply(str: Link): Option[Contact] =
      Option(str.value.toString).filter(_.size < 1000).flatMap {
        case emailPattern(mail) => Contact.Email(mail).some
        case phonePattern(value) =>
          Contact.Phone(invalidPhoneCharacters.replaceAllIn(value, "")).some
        case IsSocialNetwork(network) =>
          Contact.Social(network, str.value).some
        case _ => None
      }
  }

  object IsSocialNetwork {
    private def website(pattern: String) =
      s"""^(?:https?://)(?:www.)?${pattern}$$""".r

    private val linkedin = website("linkedin\\.com/in/.+/?")
    private val linkedinCompany = website("linkedin\\.com/company/.+/?")
    private val telegram = website("t\\.me/.+/?")
    private val instagram = website("instagram\\.com/[^/]+/?")
    private val twitter = website(
      "twitter\\.com/(?!home|share)[^/]+/?(?:\\?[^/]*)?"
    )
    private val facebook = website("facebook\\.com/[a-zA-Z0-9\\-_]{3,}/?")
    private val youtube = website("youtube\\.com/(?:@.+|c/.+)/?")
    private val github = website("github\\.(?:com|io)/[^/]+/?")
    private val gitlab = website("gitlab\\.(?:com|io)/[^/]+/?")
    private val pinterest = website("pinterest\\.com/.+/?")
    private val medium = website("medium\\.com/@[^/]+/?")
    private val tiktok = website("tiktok\\.com/@[^/]+/?")
    private val twitch = website("twitch\\.tv/[^/]+/?")
    private val crunchbase = website("crunchbase\\.com/organization/.+/?")

    def unapply(str: String): Option[SocialNetwork] =
      Option(str).filter(_.size <= 1000).flatMap {
        case linkedin()        => Some(SocialNetwork.Linkedin)
        case linkedinCompany() => Some(SocialNetwork.LinkedinCompany)
        case instagram()       => Some(SocialNetwork.Instagram)
        case twitter()         => Some(SocialNetwork.Twitter)
        case facebook()        => Some(SocialNetwork.Facebook)
        case youtube()         => Some(SocialNetwork.Youtube)
        case telegram()        => Some(SocialNetwork.Telegram)
        case github()          => Some(SocialNetwork.Github)
        case gitlab()          => Some(SocialNetwork.Gitlab)
        case pinterest()       => Some(SocialNetwork.Pinterest)
        case medium()          => Some(SocialNetwork.Medium)
        case tiktok()          => Some(SocialNetwork.TikTok)
        case twitch()          => Some(SocialNetwork.Twitch)
        case crunchbase()      => Some(SocialNetwork.Crunchbase)
        case _                 => None
      }
  }

  // https://www.rfc-editor.org/info/rfc5322
  // private val email = """[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+""".r
  //
  // Modified version of RFC one, which eliminates emails with weird characters,
  // and direct TLDs
  private val email = """[a-zA-Z0-9_+.-]+@[a-zA-Z0-9.-]+\.[a-zA-Z0-9]+""".r
  private val phonePattern =
    """\+?\s*(?:(?:\d{2,}|\(\d{2,}\))[.\- ]?)+""".r
  val body: DataExtractor = in =>
    ExtractedData(
      contacts = in.page.texts
        .flatMap(emailsIn(_))
        .toSet // ++ in.page.texts.flatMap(phonesIn).toSet
    )

  private[ankabot] def phonesIn(str: String): Iterator[Contact] =
    phonePattern
      .findAllIn(str)
      .map(_.trim)
      .filterNot(notAPhoneNumber)
      .map(Contact.Phone(_))

  private[ankabot] def emailsIn(str: String): Set[Contact] =
    email.findAllIn(str).map(s => Contact.Email(s.trim)).toSet

  private val yearPattern = """.*\b(\d{4})\b.*""".r
  private def looksLikeDate(str: String) = str match {
    case yearPattern(numbers*) =>
      numbers.map(_.toInt).exists(n => n >= 1900 && n <= 2100)
    case _ => false
  }

  private def notAPhoneNumber(phone: String) =
    phone.size <= 8 || looksLikeDate(phone)

  private val extractors = Seq(links, body)
  def all: DataExtractor = in => extractors.map(_.apply(in)).combineAll

}
