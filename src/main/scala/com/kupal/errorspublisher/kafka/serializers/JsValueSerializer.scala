package com.kupal.errorspublisher.kafka.serializers

import java.io.UnsupportedEncodingException
import java.util

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Serializer
import play.api.libs.json.JsValue

class JsValueSerializer extends Serializer[JsValue] {
  private val encoding = "UTF8"

  override def serialize(topic: String, data: JsValue): Array[Byte] = {
    val opData: Option[JsValue] = Option(data)
    try {
      opData.map(_.toString.getBytes(encoding)).orNull
    } catch {
      case e: UnsupportedEncodingException =>
        throw new SerializationException("Error when serializing JsValue (toString) to Array[Byte] due to unsupported encoding " + encoding)
    }
  }

  override def close(): Unit = {
    // nothing to do
  }

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {
    // Do nothing
  }
}
