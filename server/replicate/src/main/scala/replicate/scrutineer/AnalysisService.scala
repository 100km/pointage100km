package replicate.scrutineer

import akka.Done
import akka.stream.Attributes.Name
import akka.stream._
import akka.stream.scaladsl.{Flow, Keep, Sink}
import net.rfc1149.canape.Database
import replicate.alerts.Alerts
import replicate.messaging
import replicate.messaging.Message
import replicate.messaging.Message.{Checkpoint, Severity}
import replicate.scrutineer.Analyzer.ContestantAnalysis

import scala.concurrent.{ExecutionContext, Future}

object AnalysisService {

  def analysisServiceSink(database: Database)(implicit fm: Materializer, ec: ExecutionContext): Sink[ContestantAnalysis, Future[Done]] =
    Flow[ContestantAnalysis]
      .mapAsyncUnordered(1) { analysis ⇒
        database.updateBody("replicate", "set-analysis", analysis.id, analysis).map((analysis, _))
      }.map {
        case (analysis, response) if response.status.isFailure() ⇒
          Alerts.sendAlert(messaging.Message(Checkpoint, Severity.Error, s"Analysis for contestant ${analysis.contestantId}",
            s"Unable to store to database: ${response.status}"))
        case _ ⇒
      }.withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider) and Name("analysisServiceSink"))
      .toMat(Sink.ignore)(Keep.right)

}
