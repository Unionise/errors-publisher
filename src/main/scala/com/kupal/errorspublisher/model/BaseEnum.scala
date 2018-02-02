package com.kupal.errorspublisher.model

import play.api.libs.json._

abstract class BaseEnum[T](implicit r: play.api.libs.json.Reads[T], w: play.api.libs.json.Writes[T]) {

  trait Value {
    def value: T
  }

  case class ErroneousValue(value: T) extends Value

  def values: Seq[Value]

  def fromValue(value: T): Value = values.find(_.value == value) match {
    case Some(found) => found
    case _ => ErroneousValue(value)
  }

  implicit val reads: Reads[Value] = JsPath.read[T].map(fromValue)
  implicit val writes: Writes[Value] = (o: BaseEnum.this.Value) => Json.toJson(o.value)

}
