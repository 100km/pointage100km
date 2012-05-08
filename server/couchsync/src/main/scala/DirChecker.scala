import java.io.File
import scala.annotation.tailrec

class DirChecker(base: File, milliseconds: Long) {

  private[this] var cache: Set[File] = Set()

  private[this] def newDirectories = {
    val files = base.listFiles.toSet
    val newFiles = files -- cache
    cache = files
    newFiles filter { _.isDirectory }
  }

  @tailrec
  private[this] def candidates: Set[File] =
    newDirectories match {
	case s: Set[_] if s.isEmpty =>
	  Thread.sleep(milliseconds)
	  candidates
	case s: Set[_] =>
	  s
    }

  def suitable: File = candidates.first

}
