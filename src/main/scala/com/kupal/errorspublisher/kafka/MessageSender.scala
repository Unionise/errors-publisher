package com.kupal.errorspublisher.kafka

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.google.inject.{Inject, Singleton}
import org.apache.kafka.clients.producer.{KafkaProducer, Producer, ProducerConfig, ProducerRecord}
import play.api.Configuration
import play.api.libs.json.JsValue

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class MessageSender @Inject() (configuration: Configuration) {

  private var producerOpt: Option[Producer[String, JsValue]] = None

  protected def createProducer(): Producer[String, JsValue] = {

    val endpoints = configuration.get[String]("errorsPublisher.kafka.endpoints")
    val serviceId = configuration.get[String]("errorsPublisher.serviceId")

    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, endpoints)
    props.put(ProducerConfig.CLIENT_ID_CONFIG, serviceId)
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "com.kupal.errorspublisher.kafka.serializers.JsValueSerializer")

    new KafkaProducer[String, JsValue](props)
  }

  def send(topicNameKey: String, message: JsValue): Future[Unit] = {
    val producer = synchronized {
      producerOpt match {
        case Some(p) => p

        case None =>
          val newProducer = createProducer()

          producerOpt = Some(newProducer)

          newProducer
      }
    }

    val topic = configuration.get[String](topicNameKey)

    val record = new ProducerRecord[String, JsValue](topic, message)

    Try(producer.send(record).get(60, TimeUnit.SECONDS)) match {
      case Success(_) => Future.successful(())
      case Failure(throwable) => Future.failed(throwable)
    }
  }

}
