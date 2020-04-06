package com.ubirch.util

import org.joda.time.format.{ DateTimeFormatter, ISODateTimeFormat }
import org.joda.time.{ DateTime, DateTimeZone, LocalTime, Period }

/**
  * Convenience for Dates
  */
object DateUtil {

  def nowUTC: DateTime = DateTime.now(DateTimeZone.UTC)

  def todayAtMidnight: DateTime = nowUTC.withTime(LocalTime.MIDNIGHT)

  def parseDateToUTC(dateString: String): DateTime = {

    ISODateTimeFormat.dateTime()
      .parseDateTime(dateString + "T00:00:00.000Z")
      .withZone(DateTimeZone.UTC)

  }

  def ISOFormatter: DateTimeFormatter = ISODateTimeFormat.dateTime().withZoneUTC()

  def dateRange(from: DateTime, to: DateTime, stepSize: Period): Seq[DateTime] = {

    val now = nowUTC
    if (now.isAfter(now.plus(stepSize))) {

      Seq.empty

    } else {

      if (from.isBefore(to)) {
        Iterator.iterate(from)(_.plus(stepSize)).takeWhile(!_.isAfter(to)).toSeq
      } else {
        Iterator.iterate(from)(_.minus(stepSize)).takeWhile(!_.isBefore(to)).toSeq
      }

    }

  }

  def toString_YYYY_MM_dd(date: DateTime): String = date.toString("YYYY-MM-dd")

}
