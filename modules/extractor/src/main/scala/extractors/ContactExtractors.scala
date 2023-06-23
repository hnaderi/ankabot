package dev.hnaderi.ankabot
package extractors

import cats.syntax.all.*

object ContactExtractors {
  val links: DataExtractor = in =>
    ExtractedData(
      contacts = in.page.links.collect { case IsContact(value) => value }
    )

  private object IsContact {
    private val emailPattern = """mailto:(.+@.+)""".r
    private val phonePattern = """tel:(.{8,})""".r
    private val invalidPhoneCharacters = "[^+0-9]".r

    def unapply(str: Link): Option[Contact] =
      str.value.toString match {
        case emailPattern(mail) => Contact.Email(mail).some
        case phonePattern(value) =>
          Contact.Phone(invalidPhoneCharacters.replaceAllIn(value, "")).some
        case IsSocialNetwork(network) =>
          Contact.Social(network, str.value).some
        case _ => None
      }
  }

  private object IsSocialNetwork {
    private def website(pattern: String) =
      s"""(?:https?://)(?:www.)?${pattern}""".r

    private val linkedin = website("linkedin\\.com/in/.+")
    private val telegram = website("t\\.me/.+")
    private val instagram = website("instagram\\.com/.+")
    private val twitter = website("twitter\\.com/.+")
    private val facebook = website("facebook\\.com/.+")
    private val youtube = website("youtube\\.com/channel/.+")

    def unapply(str: String): Option[SocialNetwork] =
      str match {
        case linkedin()  => Some(SocialNetwork.Linkedin)
        case instagram() => Some(SocialNetwork.Instagram)
        case twitter()   => Some(SocialNetwork.Twitter)
        case facebook()  => Some(SocialNetwork.Facebook)
        case youtube()   => Some(SocialNetwork.Youtube)
        case telegram()  => Some(SocialNetwork.Telegram)
        case _           => None
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
