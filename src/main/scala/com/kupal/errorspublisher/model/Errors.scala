package com.kupal.errorspublisher.model

import com.kupal.errorspublisher.helpers.JsonValueEnum
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.*
import play.api.libs.functional.syntax.*
import play.api.libs.mailer.Email
import play.api.mvc.RequestHeader
import play.libs.exception.ExceptionUtils

import scala.jdk.CollectionConverters.MapHasAsScala

enum TicketStatus(val value: Int) extends JsonValueEnum[Int]:
  case Open extends TicketStatus(2)
  case Pending extends TicketStatus(3)
  case Resolved extends TicketStatus(4)
  case Closed extends TicketStatus(5)

  case ErroneousValue(raw: Int) extends TicketStatus(raw)

object TicketStatus:
  val knownValues: Seq[TicketStatus] =
    Seq(Open, Pending, Resolved, Closed)

  def fromValue(value: Int): TicketStatus =
    knownValues.find(_.value == value).getOrElse(ErroneousValue(value))

  given Format[TicketStatus] =
    JsonValueEnum.format[Int, TicketStatus](fromValue)

enum TicketPriority(val value: Int) extends JsonValueEnum[Int]:
  case Low extends TicketPriority(1)
  case Medium extends TicketPriority(2)
  case High extends TicketPriority(3)
  case Urgent extends TicketPriority(4)

  case ErroneousValue(raw: Int) extends TicketPriority(raw)

object TicketPriority:
  val knownValues: Seq[TicketPriority] = Seq(Low, Medium, High, Urgent)

  def fromValue(value: Int): TicketPriority =
    knownValues.find(_.value == value).getOrElse(ErroneousValue(value))

  given Format[TicketPriority] = JsonValueEnum.format[Int, TicketPriority](fromValue)

trait ErrorFormat {
  def lineSeparator: String

  def largeLineSeparator: String = lineSeparator + lineSeparator

  def startLineSeparator: String

  def endLineSeparator: String

  def shift: String
}

case object EmailErrorFormat extends ErrorFormat {
  override def lineSeparator: String = "\n"

  override def startLineSeparator: String = "\n"

  override def endLineSeparator: String = "\n"

  override def shift: String = "\t"
}

case object HtmlErrorFormat extends ErrorFormat {
  override def lineSeparator: String = "<br />"

  override def startLineSeparator: String = "<br />"

  override def endLineSeparator: String = "<br />"

  override def shift: String = "&nbsp;&nbsp;&nbsp;&nbsp;"
}

object Errors {

  case class ErrorMessage(
      idempotencyKey: Option[String],
      title: String,
      body: JsValue,
      tags: Seq[String],
      priority: TicketPriority,
      errorCode: Option[String],
      errorTime: DateTime)

  /** Create error message for sending it to kafka based on some erroneous event.
    *
    * @param subject
    *   subject of message
    * @param priority
    *   priority of message, default value is Medium
    * @param tags
    *   tags related to occurred erroneous event
    * @param idempotencyKey
    *   idempotency key for message, default value is None
    * @param body
    *   body of message, default value is empty string
    */

  def createKafkaMessage(
      subject: String,
      priority: TicketPriority = TicketPriority.Medium,
      tags: Seq[String] = Seq.empty,
      idempotencyKey: Option[String] = None,
      body: JsValue = JsString("")): ErrorMessage =
    ErrorMessage(
      idempotencyKey = idempotencyKey,
      title = subject,
      body = body,
      tags = tags,
      priority = priority,
      errorCode = None,
      errorTime = DateTime.now()
    )

  /** Create error message for sending it to kafka based on occurred exception.
    *
    * @param subject
    *   subject of message
    * @param throwable
    *   occurred exception
    * @param tags
    *   tags related to occurred error
    * @return
    */
  def kafkaMessageForThrowable(
      subject: String,
      throwable: Throwable,
      tags: Seq[String],
      idempotencyKey: Option[String] = None): ErrorMessage = ErrorMessage(
    idempotencyKey = idempotencyKey,
    title = s"$subject - ${subjectForThrowable(throwable)}",
    body = Json.toJson(bodyForThrowable(throwable, HtmlErrorFormat)),
    tags = tags,
    priority = TicketPriority.Medium,
    errorCode = None,
    errorTime = DateTime.now()
  )

  /** Create error message for sending it to kafka based on occurred exception during some HTTP request.
    *
    * @param request
    *   failed request
    * @param throwable
    *   occurred exception
    * @return
    *   constructed error message
    */
  def kafkaMessageForThrowableInRequest(
      request: RequestHeader,
      throwable: Throwable,
      idempotencyKey: Option[String] = None): ErrorMessage = ErrorMessage(
    idempotencyKey = idempotencyKey,
    title = subjectForThrowableInRequest(request, throwable),
    body = Json.toJson(bodyForThrowableInRequest(request, throwable, HtmlErrorFormat)),
    tags = Seq("request-exception"),
    priority = TicketPriority.Medium,
    errorCode = None,
    errorTime = DateTime.now()
  )

  /** Create email based on occurred exception during some HTTP request.
    *
    * @param recipients
    *   recipients of the email
    * @param from
    *   email of sender
    * @param request
    *   failed request
    * @param throwable
    *   occurred exception
    * @return
    *   composed email
    */
  def emailForThrowableInRequest(
      recipients: Seq[String],
      from: String,
      request: RequestHeader,
      throwable: Throwable): Email =
    Email(
      subject = subjectForThrowableInRequest(request, throwable),
      from = from,
      to = recipients,
      bodyText = Some(bodyForThrowableInRequest(request, throwable, EmailErrorFormat))
    )

  private def subjectForThrowableInRequest(request: RequestHeader, throwable: Throwable) =
    s"[${request.host}] ${subjectForThrowable(throwable)}"

  private def subjectForThrowable(throwable: Throwable) = throwable.getMessage.replaceAll("[\r\n\t]", " ")

  private def formatBody(body: String, format: ErrorFormat): String =
    body
      .replaceAll("[\r\n]", format.lineSeparator)
      .replaceAll("[\t]", format.shift)

  private def bodyForThrowable(throwable: Throwable, format: ErrorFormat): String = {
    val threadsStatus = allThreadsStackTraces(format)
    val stackTrace = ExceptionUtils.getStackTrace(throwable)

    val body =
      s"""Message:
         |${throwable.getMessage}
         |
         |Stack trace:
         |$stackTrace
         |
         |Other threads status:
         |$threadsStatus""".stripMargin

    formatBody(body, format)
  }

  private def bodyForThrowableInRequest(request: RequestHeader, throwable: Throwable, format: ErrorFormat) = {
    val throwableBody = bodyForThrowable(throwable, format)

    val body =
      s"""$throwableBody
         |
         |Request label: ${requestLabel(request)}""".stripMargin

    formatBody(body, format)
  }

  private def allThreadsStackTraces(format: ErrorFormat): String =
    Thread.getAllStackTraces.asScala.map { case (thread, stackElements) =>
      val threadDesc = s"${thread.getName} [${thread.getState}] from ${thread.getThreadGroup}:"
      (Vector(threadDesc) ++ stackElements.map(el => s"${format.shift}at $el").toVector).mkString(format.lineSeparator)
    }.mkString(format.startLineSeparator, format.largeLineSeparator, format.endLineSeparator)

  private def requestLabel(request: RequestHeader): String = {
    val hash = System.identityHashCode(request)
    Integer.toHexString(hash)
  }

  private val dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss ZZZ")

  private def dateToString(dateTime: DateTime): String = dateTimeFormatter.print(dateTime)

  private def parsePriority(maybePriority: Option[Int]): TicketPriority = maybePriority match {
    case Some(priority) =>
      TicketPriority.fromValue(priority) match {
        case TicketPriority.ErroneousValue(_) => TicketPriority.Low
        case parsedPriority                   => parsedPriority
      }

    case None => TicketPriority.Low
  }

  implicit val DateTimeWrites: Writes[DateTime] = (o: DateTime) => Json.toJson(dateTimeFormatter.print(o))

  implicit val Writes: Writes[ErrorMessage] = (
    (JsPath \ "idempotencyKey").writeNullable[String] and
      (JsPath \ "title").write[String] and
      (JsPath \ "body").write[JsValue] and
      (JsPath \ "tags").write[Seq[String]] and
      (JsPath \ "priority").write[TicketPriority] and
      (JsPath \ "errorCode").writeNullable[String] and
      (JsPath \ "errorTime").write[DateTime]
  ) { errorMessage =>
    (
      errorMessage.idempotencyKey,
      errorMessage.title,
      errorMessage.body,
      errorMessage.tags,
      errorMessage.priority,
      errorMessage.errorCode,
      errorMessage.errorTime
    )

  }

}
