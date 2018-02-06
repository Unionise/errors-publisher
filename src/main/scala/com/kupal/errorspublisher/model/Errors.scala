package com.kupal.errorspublisher.model

import org.apache.commons.lang3.exception.ExceptionUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.mailer.Email
import play.api.mvc.RequestHeader

import scala.collection.JavaConverters._

object TicketStatus extends BaseEnum[Int] {
  case object Open extends TicketStatus.Value {
    override def value: Int = 2
  }

  case object Pending extends TicketStatus.Value {
    override def value: Int = 3
  }

  case object Resolved extends TicketStatus.Value {
    override def value: Int = 4
  }

  case object Closed extends TicketStatus.Value {
    override def value: Int = 5
  }

  def values = Seq(Open, Pending, Resolved, Closed)
}

object TicketPriority extends BaseEnum[Int] {
  case object Low extends TicketPriority.Value {
    override def value: Int = 1
  }

  case object Medium extends TicketPriority.Value {
    override def value: Int = 2
  }

  case object High extends TicketPriority.Value {
    override def value: Int = 3
  }

  case object Urgent extends TicketPriority.Value {
    override def value: Int = 4
  }

  def values = Seq(Low, Medium, High, Urgent)
}

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
      priority: TicketPriority.Value,
      errorCode: Option[String],
      errorTime: DateTime
  )

  /**
    * Create error message for sending it to kafka based on some erroneous event.
    *
    * @param subject subject of message
    * @param bodyParts message body parts
    * @param tags tags related to occurred erroneous event
    * @return
    */
  def kafkaMessage(subject: String,
                   priority: TicketPriority.Value = TicketPriority.Medium,
                   tags: Seq[String] = Seq.empty, idempotencyKey: Option[String] = None)
                  (bodyParts: (String, String)*): ErrorMessage = {
    val bodyContent = bodyParts.map { case (key, value) =>
      s"$key: $value"
    }.mkString("\n")

    ErrorMessage(
      idempotencyKey = idempotencyKey,
      title = subject,
      body = Json.toJson(bodyContent),
      tags = tags,
      priority = priority,
      errorCode = None,
      errorTime = DateTime.now()
    )
  }

  /**
    * Create error message for sending it to kafka based on occurred exception.
    *
    * @param subject subject of message
    * @param throwable occurred exception
    * @param tags tags related to occurred error
    * @return
    */
  def kafkaMessageForThrowable(subject: String, throwable: Throwable, tags: Seq[String], idempotencyKey: Option[String] = None): ErrorMessage = ErrorMessage(
    idempotencyKey = idempotencyKey,
    title = s"$subject - ${subjectForThrowable(throwable)}",
    body = Json.toJson(bodyForThrowable(throwable, HtmlErrorFormat)),
    tags = tags,
    priority = TicketPriority.Medium,
    errorCode = None,
    errorTime = DateTime.now()
  )

  /**
    * Create error message for sending it to kafka based on occurred exception during some HTTP request.
    *
    * @param request failed request
    * @param throwable occurred exception
    * @return constructed error message
    */
  def kafkaMessageForThrowableInRequest(request: RequestHeader, throwable: Throwable, idempotencyKey: Option[String] = None): ErrorMessage = ErrorMessage(
    idempotencyKey = idempotencyKey,
    title = subjectForThrowableInRequest(request, throwable),
    body = Json.toJson(bodyForThrowableInRequest(request, throwable, HtmlErrorFormat)),
    tags = Seq("request-exception"),
    priority = TicketPriority.Medium,
    errorCode = None,
    errorTime = DateTime.now()
  )

  /**
    * Create email based on occurred exception during some HTTP request.
    *
    * @param recipients recipients of the email
    * @param from email of sender
    * @param request failed request
    * @param throwable occurred exception
    * @return composed email
    */
  def emailForThrowableInRequest(recipients: Seq[String], from: String, request: RequestHeader, throwable: Throwable): Email = {
    Email(
      subject = subjectForThrowableInRequest(request, throwable),
      from = from,
      to = recipients,
      bodyText = Some(bodyForThrowableInRequest(request, throwable, EmailErrorFormat))
    )
  }

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
        |$threadsStatus"""
      .stripMargin

    formatBody(body, format)
  }

  private def bodyForThrowableInRequest(request: RequestHeader, throwable: Throwable, format: ErrorFormat) = {
    val throwableBody = bodyForThrowable(throwable, format)

    val body =
      s"""$throwableBody
        |
        |Request label: ${requestLabel(request)}"""
      .stripMargin

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

  private def parsePriority(maybePriority: Option[Int]): TicketPriority.Value = maybePriority match {
    case Some(priority) =>
      TicketPriority.fromValue(priority) match {
        case TicketPriority.ErroneousValue(_) => TicketPriority.Low
        case parsedPriority => parsedPriority
      }

    case None => TicketPriority.Low
  }

  implicit val DateTimeWrites: Writes[DateTime] = (o: DateTime) => Json.toJson(dateTimeFormatter.print(o))

  implicit val Writes: Writes[ErrorMessage] = (
    (JsPath \ "idempotencyKey").writeNullable[String] and
    (JsPath \ "title").write[String] and
    (JsPath \ "body").write[JsValue] and
    (JsPath \ "tags").write[Seq[String]] and
    (JsPath \ "priority").write[TicketPriority.Value] and
    (JsPath \ "errorCode").writeNullable[String] and
    (JsPath \ "errorTime").write[DateTime]
  )(unlift(ErrorMessage.unapply))

}
