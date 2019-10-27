package replicate

import replicate.messaging.Message.Severity
import replicate.messaging.Message.Severity.Severity

package object messaging {

  private[messaging] sealed trait Status
  private[messaging] case object Ok extends Status
  private[messaging] case object Notice extends Status
  private[messaging] case object Warning extends Status
  private[messaging] case object Critical extends Status

  private[messaging] val severities: Map[Status, Severity] =
    Map(Ok -> Severity.Info, Notice -> Severity.Info, Warning -> Severity.Warning, Critical -> Severity.Critical)

}
