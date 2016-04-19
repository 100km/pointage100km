package replicate.utils

import java.util.{Calendar, TimeZone}

object FormatUtils {

  def formatDate(timestamp: Long, withSeconds: Boolean = false) = {
    val calendar = Calendar.getInstance()
    calendar.setTimeInMillis(timestamp)
    calendar.setTimeZone(TimeZone.getTimeZone(Global.infos.get.timezone));
    if (withSeconds)
      "%d:%02d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
    else
      "%d:%02d".format(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
  }

  def formatSpeed(speed: Double) = "%.2f km/h".format(speed)

  def formatDistance(distance: Double) = "%.2f km".format(distance)

}
