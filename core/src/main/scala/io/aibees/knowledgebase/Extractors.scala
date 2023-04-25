package io.aibees.knowledgebase

import cats.syntax.all.*

import java.net.URI

object Extractors {
  val links: DataExtractor = in =>
    ExtractedData(
      contacts = in.links.collect { case IsContact(value) => value }
    )

  private object IsContact {
    private val emailPattern = """mailto:(.+@.+)""".r
    private val phonePattern = """tel:(.{8,})""".r
    private val invalidPhoneCharacters = "[^+0-9]".r
    private def buildURI(str: String) = Either.catchNonFatal(URI(str)).toOption

    def unapply(str: String): Option[Contact] =
      str match {
        case emailPattern(mail) => Contact.Email(mail).some
        case phonePattern(value) =>
          Contact.Phone(invalidPhoneCharacters.replaceAllIn(value, "")).some
        case IsSocialNetwork(network) =>
          buildURI(str).map(Contact.Social(network, _))
        case _ => None
      }
  }

  private object IsSocialNetwork {
    private def website(pattern: String) =
      s"""(?:https?://)(?:www.)?${pattern}""".r

    private val linkedin = website("linkedin\\.com/in/.*")
    private val telegram = website(".*t\\.me.*")
    private val instagram = website("instagram\\.com/.*")
    private val twitter = website("twitter\\.com/.*")
    private val facebook = website("facebook\\.com/.*")
    private val youtube = website("youtube\\.com/channel.*")

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
  private val email = """[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+""".r
  private[knowledgebase] val phone =
    """\+?\s*(?:(?:\d{2,}|\(\d{2,}\))[.\- _]?)+""".r
  val body: DataExtractor = in =>
    ExtractedData(
      contacts = in.texts
        .flatMap(email.findAllIn(_))
        .map(s => Contact.Email(s.trim))
        .toSet ++
        in.texts
          .flatMap(phone.findAllIn(_))
          .filter(_.length() > 8)
          .map(s => Contact.Phone(s.trim))
          .toSet
    )

  private val extractors = Seq(links, body)
  def all: DataExtractor = in => extractors.map(_.apply(in)).combineAll

}
