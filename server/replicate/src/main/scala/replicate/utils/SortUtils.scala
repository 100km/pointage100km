package replicate.utils

import scala.collection.IndexedSeq
import scala.collection.BuildFrom
import scala.language.higherKinds

object SortUtils {

  private def insertInto[T, Repr](element: T, data: Repr)(implicit ord: Ordering[T], bf: BuildFrom[Repr, T, Repr], ev: Repr <:< IndexedSeq[T]): Repr = {
    // Perform dichotomy to find the place before which we need to insert the element
    var left = 0
    var right = data.size
    while (left != right) {
      val pivot = (left + right) / 2
      ord.compare(data(pivot), element) match {
        case 0 =>
          left = pivot
          right = pivot
        case 1 =>
          right = pivot
        case -1 =>
          if (left == pivot)
            left = pivot + 1
          else
            left = pivot
      }
    }
    val builder = bf.newBuilder(data)
    builder ++= data.take(left)
    builder += element
    builder ++= data.drop(left)
    builder.result()
  }

  implicit class WithInsert[T, Repr](data: Repr) {

    def insert(element: T)(implicit ord: Ordering[T], bf: BuildFrom[Repr, T, Repr], ev: Repr <:< IndexedSeq[T]): Repr =
      insertInto[T, Repr](element, data)

  }

}
