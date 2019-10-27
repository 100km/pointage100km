package replicate.utils

import scala.collection.IndexedSeqLike
import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

object SortUtils {

  private def insertInto[T, Repr](element: T, data: Repr)(implicit ord: Ordering[T], bf: CanBuildFrom[Repr, T, Repr], ev: Repr <:< IndexedSeqLike[T, Repr]): Repr = {
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
    val builder = bf()
    builder ++= data.take(left)
    builder += element
    builder ++= data.drop(left)
    builder.result()
  }

  implicit class WithInsert[T, Repr](data: Repr) {

    def insert(element: T)(implicit ord: Ordering[T], bf: CanBuildFrom[Repr, T, Repr], ev: Repr <:< IndexedSeqLike[T, Repr]): Repr =
      insertInto[T, Repr](element, data)

    def insertionSorted(implicit ord: Ordering[T], bf: CanBuildFrom[Repr, T, Repr], ev: Repr <:< IndexedSeqLike[T, Repr]): Repr =
      data.foldLeft(bf().result()) { case (b, e) => insertInto(e, b) }
  }

}
