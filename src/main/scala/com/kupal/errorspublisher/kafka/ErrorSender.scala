package com.kupal.errorspublisher.kafka

import com.google.inject.{Inject, Singleton}
import com.kupal.errorspublisher.model.Errors.ErrorMessage
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.Future

@Singleton
class ErrorSender @Inject() (sender: MessageSender, configuration: Configuration) {

  private val serviceId: String = configuration.get[String]("errorsPublisher.serviceId")

  def sendError(error: ErrorMessage): Future[Unit] = {
    sender.send(
      "errorsPublisher.kafka.channels.errors.topic",
      Json.toJson(error).as[JsObject] + ("serviceId" -> Json.toJson(serviceId))
    )
  }

}
