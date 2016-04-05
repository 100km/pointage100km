package replicate.scrutineer

import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import net.rfc1149.canape.Database
import replicate.state.CheckpointsState
import replicate.state.CheckpointsState.{CheckpointData, Point}

import scala.concurrent.Future

object CheckpointScrutineer {

  def startCheckpointScrutineer(database: Database)(implicit log: LoggingAdapter, fm: Materializer) = {
    import fm.executionContext

    val source = Source.fromFuture(database.viewWithUpdateSeq[Int, CheckpointData]("replicate", "checkpoint", Seq("include_docs" → "true")))
      .flatMapConcat {
        case (lastSeq, checkpoints) ⇒
          val groupedByContestants = Source(checkpoints.filterNot(_._2.raceId == 0).groupBy(_._1).map(_._2.map(_._2)))
          val enterAndKeepLatest = groupedByContestants.mapAsync(1)(cps ⇒ Future.sequence(cps.dropRight(1).map(_.pristine).map(CheckpointsState.setTimes)).map(_ ⇒ cps.last))
          val changes =
            database.changesSource(Map("filter" → "_view", "view" → "replicate/checkpoint", "include_docs" → "true"), sinceSeq = lastSeq)
              .map(js ⇒ (js \ "doc").as[CheckpointData]).map(_.pristine)
          enterAndKeepLatest ++ changes
      }

    val checkpointDataToPoints = Flow[CheckpointData].mapAsync(1) {
      case checkpointData ⇒
        CheckpointsState.setTimes(checkpointData).map((checkpointData, _))
    }

    val pointsToAnalyzed = Flow[(CheckpointData, Seq[Point])].mapConcat {
      case (checkpointData, points) ⇒
        try {
          List(Analyzer.analyze(checkpointData.raceId, checkpointData.contestantId, points))
        } catch {
          case t: Throwable ⇒
            log.error(t, "unable to analyse contestant {} in race {}", checkpointData.contestantId, checkpointData.raceId)
            Nil
        }
    }

    source.via(checkpointDataToPoints).via(pointsToAnalyzed).to(ProblemService.problemServiceSink(database)).run()
  }

}
