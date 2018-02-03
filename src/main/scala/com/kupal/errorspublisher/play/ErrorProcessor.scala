package com.kupal.errorspublisher.play

import javax.inject.{Inject, Singleton}

import com.kupal.errorspublisher.kafka.{ErrorSender, MessageSender}
import com.kupal.errorspublisher.model.Errors
import play.api.Configuration
import play.api.libs.mailer.MailerClient
import play.api.mvc._

import scala.concurrent._
import scala.util.{Failure, Success, Try}

@Singleton
class ErrorProcessor @Inject()(
    configuration: Configuration,
    mailerClient: MailerClient,
    producerFactory: MessageSender,
    errorSender: ErrorSender,
    implicit val ec: ExecutionContext
) {

  val logger = play.api.Logger(this.getClass)

  private def processErrorThroughKafka(request: RequestHeader, exception: Throwable): Future[Unit] = {
    errorSender.sendError(
      Errors.kafkaMessageForThrowableInRequest(request, exception)
    )
  }

  def processRequestError(request: RequestHeader, exception: Throwable): Future[Unit] = {
    if (configuration.get[Boolean]("errorsPublisher.enabled")) {
      configuration.get[String]("errorsPublisher.mode") match {
        case "kafka" => processErrorThroughKafka(request, exception).transformWith {
          case Success(_) => Future.successful(())
          case Failure(_) => processErrorThroughEmail(request, exception)
        }

        case _ => processErrorThroughEmail(request, exception)
      }
    } else {
      Future.successful(())
    }
  }

  private def processErrorThroughEmail(request: RequestHeader, exception: Throwable): Future[Unit] = {
    if (request.host.startsWith("localhost") && configuration.get[Boolean]("errorsPublisher.disabledOnLocalhost")) {
      Future.successful(())
    } else {
      Try {
        Future {
          val recipients = configuration.get[Seq[String]]("errorsPublisher.mailer.recipients")
          val from = configuration.get[String]("errorsPublisher.mailer.from")
          val emailMessage = Errors.emailForThrowableInRequest(recipients, from, request, exception)

          logger.trace(s"Send error through email. Recipients: $recipients, from: $from. Message: $emailMessage")

          mailerClient.send(emailMessage)
        }
      } match {

        case Failure(f) => Future.successful(logger.error("Could not log error", f))

        case _ => Future.successful(())

      }
    }
  }

}
