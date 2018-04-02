package com.kupal.errorspublisher.kafka

import com.google.inject.{Inject, Singleton}
import com.kupal.errorspublisher.helpers.Logging
import com.kupal.errorspublisher.model.Errors.ErrorMessage
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

@Singleton
class ErrorSender @Inject() (sender: MessageSender, configuration: Configuration) extends Logging {

  private val serviceId: String = configuration.get[String]("errorsPublisher.serviceId")
  private val errorPublishingEnabled = configuration.get[Boolean]("errorsPublisher.enabled")
  private val inKafkaMode = configuration.get[String]("errorsPublisher.mode") == "kafka"

  logger.trace(s"Error sender '$serviceId' created.")

  def sendError(error: ErrorMessage): Future[Unit] = {
    if (errorPublishingEnabled && inKafkaMode) {
      sender.send(
        "errorsPublisher.kafka.channels.errors.topic",
        Json.toJson(error).as[JsObject] + ("serviceId" -> Json.toJson(serviceId))
      )
    } else {
      Future.successful(())
    }
  }

}
