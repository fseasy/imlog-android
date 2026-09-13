package top.fseasy.imlog.ui.util

import android.content.Context
import android.text.format.DateFormat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import java.time.Instant as JavaInstant

fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(this.epochSeconds, this.nanosecondsOfSecond.toLong())

/** KMP version */
fun kotlin.time.Instant.toLocaleEpochDays(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
    toLocalDateTime(timeZone).date.toEpochDays()

/**
 * High-performance IM timestamp formatter. Optimized for 120fps scrolling: uses Long range checks
 * and cached thread-safe formatters.
 */
object ImTimeUtils {

  @Volatile private var timeBoundaryCache: TimeBoundaries? = null

  @Volatile private var formatterCache: FormatterHolder? = null

  /**
   * Formats message bubble time (e.g. "14:32" or "2:32 PM"). Zero allocation except for the
   * resulting String.
   */
  fun formatMessageTime(
      context: Context,
      instant: Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    val epochMillis = instant.toEpochMilliseconds()
    val is24Hour = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()

    val formatters = getFormatters(context, is24Hour, locale, zoneId)
    val javaInstant = JavaInstant.ofEpochMilli(epochMillis)
    return formatters.shortTime.format(javaInstant)
  }

  /**
   * Formats conversation summary timestamp.
   *
   * Rules (from newest to oldest):
   * 1. Today → short time
   * 2. Yesterday → "Yesterday" (localized)
   * 3. Same week → weekday name
   * 4. Same year → month + day
   * 5. Other years → medium date
   */
  fun formatImTimeSummary(
      context: Context,
      instant: Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    val epochMillis = instant.toEpochMilliseconds()
    val is24Hour = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()

    val boundaries = getTimeBoundaries(zoneId)
    val formatters = getFormatters(context, is24Hour, locale, zoneId)
    val javaInstant = JavaInstant.ofEpochMilli(epochMillis)

    return when {
      epochMillis >= boundaries.todayStartMillis &&
          epochMillis < boundaries.tomorrowStartMillis -> {
        formatters.shortTime.format(javaInstant)
      }
      epochMillis >= boundaries.yesterdayStartMillis &&
          epochMillis < boundaries.todayStartMillis -> {
        formatters.yesterdayLabel
      }
      epochMillis >= boundaries.weekStartMillis -> {
        formatters.weekday.format(javaInstant)
      }
      epochMillis >= boundaries.yearStartMillis -> {
        formatters.monthDay.format(javaInstant)
      }
      else -> {
        formatters.mediumDate.format(javaInstant)
      }
    }
  }

  /**
   * Formats chat date separator. Similar to formatImTimeSummary, but return `Today` when it's the
   * same day.
   *
   * Rules (from newest to oldest):
   * 1. Today → "Today" (localized)
   * 2. Yesterday → "Yesterday" (localized)
   * 3. Same week → weekday name
   * 4. Same year → month + day
   * 5. Other years → medium date
   */
  fun formatImDay(
      context: Context,
      instant: Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
  ): String {
    val epochMillis = instant.toEpochMilliseconds()
    val is24Hour = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()

    val boundaries = getTimeBoundaries(zoneId)
    val formatters = getFormatters(context, is24Hour, locale, zoneId)
    val javaInstant = JavaInstant.ofEpochMilli(epochMillis)

    return when {
      epochMillis >= boundaries.todayStartMillis &&
          epochMillis < boundaries.tomorrowStartMillis -> {
        formatters.todayLabel
      }
      epochMillis >= boundaries.yesterdayStartMillis &&
          epochMillis < boundaries.todayStartMillis -> {
        formatters.yesterdayLabel
      }
      epochMillis >= boundaries.weekStartMillis -> {
        formatters.weekday.format(javaInstant)
      }
      epochMillis >= boundaries.yearStartMillis -> {
        formatters.monthDay.format(javaInstant)
      }
      else -> {
        formatters.mediumDate.format(javaInstant)
      }
    }
  }

  // --- Internal Caching & Boundary Math ---

  private class TimeBoundaries(
      val dayEpoch: Long,
      val tomorrowStartMillis: Long,
      val todayStartMillis: Long,
      val yesterdayStartMillis: Long,
      val weekStartMillis: Long,
      val yearStartMillis: Long,
  )

  private class FormatterHolder(
      val locale: Locale,
      val is24Hour: Boolean,
      val zoneId: ZoneId,
      val shortTime: DateTimeFormatter,
      val weekday: DateTimeFormatter,
      val monthDay: DateTimeFormatter,
      val mediumDate: DateTimeFormatter,
      val todayLabel: String,
      val yesterdayLabel: String,
  )

  /**
   * Caches time boundaries (midnight of today, yesterday, this week, this year). Automatically
   * refreshes when the calendar day rolls over.
   */
  private fun getTimeBoundaries(zoneId: ZoneId): TimeBoundaries {
    val now = JavaInstant.now()
    val today = now.atZone(zoneId).toLocalDate()
    val currentDayEpoch = today.toEpochDay()

    val cached = timeBoundaryCache
    if (cached != null && cached.dayEpoch == currentDayEpoch) {
      return cached
    }

    val todayStart = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val tomorrowStart = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val yesterdayStart = today.minusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    // Monday as the week start
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekStart = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()

    val yearStart = LocalDate.of(today.year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    return TimeBoundaries(
            dayEpoch = currentDayEpoch,
            tomorrowStartMillis = tomorrowStart,
            todayStartMillis = todayStart,
            yesterdayStartMillis = yesterdayStart,
            weekStartMillis = weekStart,
            yearStartMillis = yearStart,
        )
        .also { timeBoundaryCache = it }
  }

  /** Thread-safe cache for DateTimeFormatter and static labels. */
  private fun getFormatters(
      context: Context,
      is24Hour: Boolean,
      locale: Locale,
      zoneId: ZoneId,
  ): FormatterHolder {
    val cached = formatterCache
    if (
        cached != null &&
            cached.locale == locale &&
            cached.is24Hour == is24Hour &&
            cached.zoneId == zoneId
    ) {
      return cached
    }

    // get the short time pattern
    val shortTimeSkeleton = if (is24Hour) "Hm" else "hm"
    val shortTimePattern = DateFormat.getBestDateTimePattern(locale, shortTimeSkeleton)
    val monthDayPattern = DateFormat.getBestDateTimePattern(locale, "MMMd")
    val weekdayPattern = DateFormat.getBestDateTimePattern(locale, "EEEE")
    val mediumDatePattern = DateFormat.getBestDateTimePattern(locale, "yMMMd")

    return FormatterHolder(
            locale = locale,
            is24Hour = is24Hour,
            zoneId = zoneId,
            shortTime = DateTimeFormatter.ofPattern(shortTimePattern, locale).withZone(zoneId),
            weekday = DateTimeFormatter.ofPattern(weekdayPattern, locale).withZone(zoneId),
            monthDay = DateTimeFormatter.ofPattern(monthDayPattern, locale).withZone(zoneId),
            mediumDate = DateTimeFormatter.ofPattern(mediumDatePattern, locale).withZone(zoneId),
            todayLabel = resolveRelativeDay(locale, isToday = true),
            yesterdayLabel = resolveRelativeDay(locale, isToday = false),
        )
        .also { formatterCache = it }
  }

  private fun resolveRelativeDay(locale: Locale, isToday: Boolean): String {
    return runCatching {
      val direction =
          if (isToday) {
            android.icu.text.RelativeDateTimeFormatter.Direction.THIS
          } else {
            android.icu.text.RelativeDateTimeFormatter.Direction.LAST
          }
      val formatter = android.icu.text.RelativeDateTimeFormatter.getInstance(locale)
      val raw =
          formatter.format(
              direction,
              android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit.DAY,
          )
      raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
        .getOrElse { if (isToday) "Today" else "Yesterday" }
  }
}
