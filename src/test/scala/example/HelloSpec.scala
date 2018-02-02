package example

import org.scalatest._

class HelloSpec extends FlatSpec with Matchers {
  "Test" should "check something important" in {
    true === true
  }
}
