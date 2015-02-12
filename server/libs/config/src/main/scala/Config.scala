import java.io.File
import org.ini4j.{Ini, Profile}

class Config(file: File) extends Ini(file) {

  def getSection(section: String): Option[Profile.Section] =
    get(section) match {
      case null => None
      case s    => Some(s)
    }

  def get[T](section: String, key: String)(implicit m: Manifest[T]): Option[T] =
    getSection(section) flatMap {
      _.get(key, 0, m.runtimeClass) match {
	case null => None
	case v    => Some(v.asInstanceOf[T])
      }
    }

  def readOpt[T: Manifest](fullName: String): Option[T] =
    fullName.split("""\.""", 2) match {
      case Array(secName, keyName) => get[T](secName, keyName)
      case _                       => None
    }

  def read[T: Manifest](fullName: String): T = readOpt[T](fullName).get
}

object Config {

  def apply(file: File): Config = new Config(file)

  def apply(fileNames: String*): Config =
    try {
      new Config(new File(fileNames.head))
    } catch {
      case _: java.io.FileNotFoundException if fileNames.size > 1 =>
	apply(fileNames.tail: _*)
    }

}
