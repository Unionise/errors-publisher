package com.kupal.errorspublisher.kafka.serializers

import java.io.UnsupportedEncodingException
import java.util

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import play.api.libs.json.{JsValue, Json}

class JsValueDeserializer extends Deserializer[JsValue] {
  private val encoding = "UTF8"

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    // Do nothing
  }

  override def deserialize(topic: String, data: Array[Byte]): JsValue = {
    val opData: Option[Array[Byte]] = Option(data)
    try {
      opData.map(new String(_, encoding)).map(Json.parse).orNull
    } catch {
      case _: UnsupportedEncodingException =>
        throw new SerializationException("Error when deserializing Array[Byte] to (string) JsValue due to unsupported encoding " + encoding);
    }
  }

  override def close(): Unit = {
    // nothing to do
  }
}
