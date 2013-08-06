package play.modules
package mailer

import java.util.Date
import java.util.Properties

import javax.activation.DataHandler
import javax.mail._
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import play.api.Play.current

import scala.language.implicitConversions
import scala.concurrent.{ Future, ExecutionContext }
import scala.util.Try
import play.api.Logger
import scala.Some
import scala.util.Success

trait Mailer {

  def session: Session = {
    val properties = new Properties()
    properties.put("mail.transport.protocol", keys.protocol)
    properties.put("mail.smtps.quitwait", "false")
    properties.put("mail.smtps.host", keys.host)
    properties.put("mail.smtps.port", keys.port)
    properties.put("mail.smtp.ssl.enable", keys.sslEnable)
    properties.put("mail.smtp.from", keys.failTo)

    val username = keys.username
    val password = keys.password

    properties.put("mail.smtps.username", username)
    properties.put("mail.smtps.auth", "true")

    Session.getInstance(properties, new Authenticator {

      override def getPasswordAuthentication = new PasswordAuthentication(username, password)

    })
  }

  private def send(transport: Try[Transport])(email: Email): Try[Email] = {
    def doSend(transport: Transport, email: Email) = {
      val message = email createFor session
      transport.sendMessage(message, message.getAllRecipients)
      email
    }
    transport.map(doSend(_, email))
  }

  def sendEmail(email: Email):Unit = {
    val transport = session.getTransport
    transport.connect()
    send(Success(transport))(email)
    transport.close()
  }

  def sendEmails(emails: Seq[Email]): Seq[Try[Email]] = {
    val transport = session.getTransport
    try {
      for {
        connection <- Seq(Try({ transport.connect(); transport }))
        email <- emails
      } yield send(connection)(email)
    } finally try {
      transport.close()
    } catch {
      case t: Throwable => Logger.error(s"Error when closing connection with mail server: ${t.getMessage}")
    }

  }

  object keys {
    lazy val protocol = PlayConfiguration("mail.transport.protocol", Some("smtps"))
    lazy val sslEnable = PlayConfiguration("mail.smtp.ssl.enable", Some("true"))
    lazy val host = PlayConfiguration("mail.smtp.host")
    lazy val port = PlayConfiguration("mail.smtp.port")
    lazy val username = PlayConfiguration("mail.smtp.username")
    lazy val password = PlayConfiguration("mail.smtp.password")
    lazy val failTo = PlayConfiguration("mail.smtp.failTo")
  }
}

trait AsyncMailer extends Mailer { 
  
  def sendEmail(email: Email)(implicit executionContext: ExecutionContext): Future[Email] = {
    import scala.concurrent.future
    future { super.sendEmail(email); email }
  }
}

object Mailer extends Mailer
object AsyncMailer extends AsyncMailer

case class Email(subject: String, from: EmailAddress, replyTo: Option[EmailAddress], recipients: Seq[Recipient], text: String, htmlText: String, attachments: Seq[Attachment]) {

  type Root = MimeMultipart
  type Related = MimeMultipart
  type Alternative = MimeMultipart

  private[mailer] def messageStructure: (Root, Related, Alternative) = {
    val root = new MimeMultipart("mixed")
    val relatedPart = new MimeBodyPart
    val related = new MimeMultipart("related")
    val alternativePart = new MimeBodyPart
    val alternative = new MimeMultipart("alternative")

    root addBodyPart relatedPart
    relatedPart setContent related

    related addBodyPart alternativePart
    alternativePart setContent alternative

    (root, related, alternative)
  }

  private[mailer] def createFor(session: Session): Message = {

    val (root, related, alternative) = messageStructure

    val message = new MimeMessage(session)
    message.setSubject(subject, "UTF-8")
    message setFrom from
    replyTo foreach (replyTo => message setReplyTo Array(replyTo))
    message setContent root
    message setSentDate new Date

    recipients foreach { r =>
      message.addRecipient(r.tpe, r.emailAddress)
    }

    val messagePart = new MimeBodyPart
    messagePart.setText(text, "UTF-8")
    alternative addBodyPart messagePart

    val messagePartHtml = new MimeBodyPart
    messagePartHtml.setContent(htmlText, "text/html; charset=\"UTF-8\"")
    alternative addBodyPart messagePartHtml

    attachments foreach { a =>
      a.disposition match {
        case Disposition.Inline => related addBodyPart a
        case Disposition.Attachment => root addBodyPart a
      }
    }

    message.saveChanges()

    message
  }

  implicit def emailAddressToInternetAddress(emailAddress: EmailAddress): InternetAddress =
    new InternetAddress(emailAddress.address, emailAddress.name)

  implicit def attachmentToMimeBodyPart(attachment: Attachment): MimeBodyPart = {
    val Attachment(name, datasource, disposition) = attachment

    val datasourceName = datasource.getName

    val attachmentPart = new MimeBodyPart
    attachmentPart.setDataHandler(new DataHandler(datasource))
    attachmentPart.setFileName(name)
    attachmentPart.setHeader("Content-Type", datasource.getContentType + "; filename=" + datasourceName + "; name=" + datasourceName)
    attachmentPart.setHeader("Content-ID", "<" + datasourceName + ">")
    attachmentPart.setDisposition(disposition.value + "; size=0")

    attachmentPart
  }

}

case class EmailAddress(name: String, address: String)
case class Recipient(tpe: RecipientType, emailAddress: EmailAddress)
case class Attachment(name: String, datasource: DataSource, disposition: Disposition)

abstract sealed class Disposition(val value: String)
object Disposition {
  case object Inline extends Disposition(Part.INLINE)
  case object Attachment extends Disposition(Part.ATTACHMENT)
}

object Attachment extends Function3[String, DataSource, Disposition, Attachment] {
  def apply(name: String, data: Array[Byte], mimeType: String): Attachment = {
    val dataSource = new ByteArrayDataSource(data, mimeType)
    dataSource setName name
    apply(name, dataSource, Disposition.Attachment)
  }

  def apply(name: String, data: Array[Byte], mimeType: String, disposition: Disposition): Attachment = {
    val dataSource = new ByteArrayDataSource(data, mimeType)
    dataSource setName name
    apply(name, dataSource, disposition)
  }
}
