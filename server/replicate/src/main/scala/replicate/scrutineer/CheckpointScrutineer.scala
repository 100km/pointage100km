package replicate.scrutineer

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.{ActorAttributes, Attributes, Materializer, Supervision}
import net.rfc1149.canape.Database
import play.api.libs.json.JsObject
import replicate.models.CheckpointData
import replicate.scrutineer.Analyzer.ContestantAnalysis
import replicate.state.CheckpointsState
import replicate.utils.Types.RaceId

import scala.concurrent.Future

object CheckpointScrutineer {

  /**
   * Return a source of live analyzed race data, starting with historical race data.
   *
   * @param database the database to connect to
   * @param log the log to use to indicate problems
   * @param fm a materializer
   * @return a source of contestant analysis data
   */
  def checkpointScrutineer(database: Database)(implicit log: LoggingAdapter, fm: Materializer): Source[(ContestantAnalysis, Boolean), NotUsed] = {
    import fm.executionContext

    val source = Source.future(database.viewWithUpdateSeq[Int, CheckpointData]("replicate", "checkpoint", Seq("include_docs" -> "true")))
      .flatMapConcat {
        case (lastSeq, checkpoints) =>
          val groupedByContestants = Source(checkpoints.filterNot(_._2.raceId == RaceId(0)).groupBy(_._1).map(_._2.map(_._2)))
          val enterAndKeepLatest = groupedByContestants.mapAsync(1)(cps => Future.sequence(cps.dropRight(1).map(CheckpointsState.setTimes)).map(_ => cps.last))
          val changes =
            database.changesSource(Map("filter" -> "_view", "view" -> "replicate/checkpoint", "include_docs" -> "true"), sinceSeq = lastSeq)
              .mapConcat { js =>
                (js \ "doc").asOpt[CheckpointData] match {
                  case Some(cpd) =>
                    List(cpd)
                  case None =>
                    log.error("unable to decode {} as valid checkpoint data", (js \ "doc").as[JsObject]); Nil
                }
              }.mapConcat {
                case cpd if cpd.raceId == 0 =>
                  log.warning("skipping checkpoint data of contestant {} at site {} because no race is defined", cpd.contestantId, cpd.siteId)
                  Nil
                case cpd =>
                  List(cpd)
              }
          enterAndKeepLatest.map((_, true)) ++ changes.map((_, false))
      }

    val checkpointDataToPoints = Flow[(CheckpointData, Boolean)]
      .mapAsync(1) { case (checkpointData, initial) => CheckpointsState.setTimes(checkpointData).map((_, initial)) }
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider) and Attributes.name("checkpointDataToPoints"))

    val pointsToAnalyzed = Flow[(IndexedSeq[CheckpointData], Boolean)].mapConcat {
      case (data, initial) =>
        try {
          Vector((Analyzer.analyze(data), initial))
        } catch {
          case t: Throwable =>
            log.error(t, "unable to analyse contestant {} in race {}", data.head.contestantId, data.head.raceId)
            Vector()
        }
    }.named("analyzer")

    // Analyze checkpoints as they arrive (after the initial batch),
    source.via(checkpointDataToPoints).via(pointsToAnalyzed)
  }

}
