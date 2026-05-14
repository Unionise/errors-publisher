package com.kupal.errorspublisher.helpers

import play.api.libs.json.*

trait JsonValueEnum[T]:
  def value: T

object JsonValueEnum:
  def reads[T: Reads, A <: JsonValueEnum[T]](fromValue: T => A): Reads[A] =
    summon[Reads[T]].map(fromValue)

  def writes[T: Writes, A <: JsonValueEnum[T]]: Writes[A] =
    Writes[A](a => Json.toJson(a.value))

  def format[T: Reads: Writes, A <: JsonValueEnum[T]](fromValue: T => A): Format[A] =
    Format(reads[T, A](fromValue), writes[T, A])