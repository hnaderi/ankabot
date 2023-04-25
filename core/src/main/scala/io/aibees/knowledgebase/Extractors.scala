package io.aibees.knowledgebase

import cats.syntax.all.*

import java.net.URI

object Extractors {
  val contacts: DataExtractor = in =>
    ExtractedData(
      contacts = in.links.collect { case IsContact(value) => value }.toList
    )

  private object IsContact {
    private val emailPattern = """mailto:(.+@.+)""".r
    private val phonePattern = """tel:(.+)""".r
    private val invalidPhoneCharacters = "[^+0-9]".r
    private def buildURI(str: String) = Either.catchNonFatal(URI(str)).toOption

    def unapply(str: String): Option[Contact] =
      str match {
        case emailPattern(mail) => Contact.Email(mail).some
        case phonePattern(value) =>
          Contact.Phone(invalidPhoneCharacters.replaceAllIn(value, "")).some
        case IsSocialContact(network) =>
          buildURI(str).map(Contact.Social(network, _))
        case _ => None
      }
  }

  private object IsSocialContact {
    private val linkedin = """.*linkedin\.com/in/.*""".r
    private val telegram = """.*t\.me.*""".r
    private val instagram = """.*instagram\.com/.*""".r
    private val twitter = """.*twitter\.com/.*""".r
    private val facebook = """.*facebook\.com/.*""".r
    private val youtube = """.*youtube\.com/channel.*""".r

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

}
