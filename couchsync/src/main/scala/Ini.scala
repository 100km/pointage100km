import java.io.Writer
import scala.io.Source

class Ini {

  private[this] var values: Map[(String, String), String] = Map()

  def load(in: Source) {
    val secHeader = """\[\s*(\S+)\s*\]"""r
    val value = """(\S+)\s*=\s*(.*\S)"""r
    var currentSection: String = "default"
    for (line <- in.getLines) {
      line.split(";", 2).head.trim match {
	case secHeader(section) => currentSection = section
	case value(k, v) => set(currentSection, k, v)
	case "" =>
	case _ => throw new RuntimeException("incorrect line: " + line)
      }
    }
  }

  def save(out: Writer) {
    for ((section, items) <- values groupBy (_._1._1)) {
      out.write(String.format("\n[%s]\n", section))
      for ((k, v) <- items)
	out.write(String.format("%s = %s\n", k._2, v))
    }
  }

  def get(section: String, key: String) = values.get((section, key))

  def set(section: String, key: String, value: Any) = values += ((section, key) -> value.toString)

}
