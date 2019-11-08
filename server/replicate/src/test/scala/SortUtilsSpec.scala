import org.specs2.mutable._
import replicate.utils.SortUtils._

class SortUtilsSpec extends Specification {

  "insert()" should {

    "insert an element into an empty vector" in {
      Vector[Int]().insert(42) should be equalTo (Vector(42))
    }

    "insert an element before one element" in {
      Vector(10).insert(5) should be equalTo (Vector(5, 10))
    }

    "insert an element after one element" in {
      Vector(5).insert(10) should be equalTo (Vector(5, 10))
    }

    "insert an element before two elements" in {
      Vector(10, 20).insert(5) should be equalTo (Vector(5, 10, 20))
    }

    "insert an element after two elements" in {
      Vector(5, 10).insert(20) should be equalTo (Vector(5, 10, 20))
    }

    "insert an element between two elements" in {
      Vector(5, 20).insert(10) should be equalTo (Vector(5, 10, 20))
    }

    "insert an element in the middle of three elements" in {
      Vector(5, 10, 15).insert(8) should be equalTo (Vector(5, 8, 10, 15))
      Vector(5, 10, 15).insert(12) should be equalTo (Vector(5, 10, 12, 15))
    }

    "insert an element equal to the first element" in {
      Vector(5, 10).insert(5) should be equalTo (Vector(5, 5, 10))
    }

    "insert an element equal to the last element" in {
      Vector(5, 10).insert(10) should be equalTo (Vector(5, 10, 10))
    }

  }

}
