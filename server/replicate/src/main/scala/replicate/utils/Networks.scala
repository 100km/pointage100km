package replicate.utils

import com.github.tototoshi.csv.CSVReader

import scala.io.Source

object Networks {

  case class Network(mcc: String, mnc: String, country: String, network: String)

  lazy val networks: List[Network] = {
    val csvReader = CSVReader.open(Source.fromInputStream(getClass.getResourceAsStream("/mcc-mnc.csv")))
    csvReader.iterator.collect {
      case Seq(mcc: String, _, mnc: String, _, _, country: String, _, network: String) if mcc != "MCC" =>
        Network(mcc, mnc, country, network.replaceAll("Lliad", "Iliad"))
    }.toList
  }

  lazy val byMCCMNC = networks.map(o => (o.mcc + o.mnc) -> o).toMap

}

