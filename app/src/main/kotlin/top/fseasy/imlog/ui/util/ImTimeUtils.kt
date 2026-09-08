package top.fseasy.imlog.ui.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun kotlin.time.Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochSecond(this.epochSeconds, this.nanosecondsOfSecond.toLong())

/** KMP version */
fun kotlin.time.Instant.toLocaleEpochDays(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
    toLocalDateTime(timeZone).date.toEpochDays()

/**
 * Utility for formatting timestamps in a WeChat / IM style. Formatters are cached for performance
 * when called frequently.
 */
object ImTimeUtils {

  /**
   * Formats an [java.time.Instant] into a localized WeChat-style time string.
   *
   * Rules (from newest to oldest):
   * 1. Today → short time
   * 2. Yesterday → "Yesterday" (localized)
   * 3. Same week → weekday name
   * 4. Same year → month + day
   * 5. Other years → medium date
   *
   * @param instant must be java.time.Instant, or transform following code to kotlin.datatime.xxx
   *   (not kotlin.time.Instant)
   *
   * TODO: This is JVM ONLY. and have to be platform dependent as it needs locale info which is hard
   *   to impl in KMP currently.
   */
  fun formatImTime(
      instant: java.time.Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
      locale: Locale = Locale.getDefault(),
  ): String {
    return when (val cat = classifyRelativeDay(instant, zoneId, locale)) {
      is RelativeDayCategory.Today -> getFormatters(locale, zoneId).shortTime.format(instant.atZone(zoneId))
      is RelativeDayCategory.Yesterday -> getLocalizedYesterday(locale)
      is RelativeDayCategory.ThisWeek -> cat.weekday
      is RelativeDayCategory.SameYear -> cat.monthDay
      is RelativeDayCategory.OtherYear -> cat.mediumDate
      is RelativeDayCategory.Future -> cat.mediumDate
    }
  }

  /**
   * Similar to formatImTime, just return `Today` when it's the same day.
   *
   * Rules (from newest to oldest):
   * 1. Today → "Today" (localized)
   * 2. Yesterday → "Yesterday" (localized)
   * 3. Same week → weekday name
   * 4. Same year → month + day
   * 5. Other years → medium date
   *
   * @param instant must be java.time.Instant, or transform following code to kotlin.datatime.xxx
   *   (not kotlin.time.Instant)
   *
   * TODO: This is JVM ONLY. and have to be platform dependent as it needs locale info which is hard
   *   to impl in KMP currently.
   */
  fun formatImDay(
      instant: java.time.Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
      locale: Locale = Locale.getDefault(),
  ): String {
    return when (val cat = classifyRelativeDay(instant, zoneId, locale)) {
      is RelativeDayCategory.Today -> getLocalizedToday(locale)
      is RelativeDayCategory.Yesterday -> getLocalizedYesterday(locale)
      is RelativeDayCategory.ThisWeek -> cat.weekday
      is RelativeDayCategory.SameYear -> cat.monthDay
      is RelativeDayCategory.OtherYear -> cat.mediumDate
      is RelativeDayCategory.Future -> cat.mediumDate
    }
  }

  // Cache: (locale + zoneId) → commonly used formatters
  private data class FormatterKey(val locale: Locale, val zoneId: ZoneId)

  private data class CachedFormatters(
      val shortTime: DateTimeFormatter,
      val weekday: DateTimeFormatter,
      val mediumDate: DateTimeFormatter,
  )

  private val formatterCache = ConcurrentHashMap<FormatterKey, CachedFormatters>()

  private sealed interface RelativeDayCategory {
    data object Today : RelativeDayCategory

    data object Yesterday : RelativeDayCategory

    data class ThisWeek(val weekday: String) : RelativeDayCategory

    data class SameYear(val monthDay: String) : RelativeDayCategory

    data class OtherYear(val mediumDate: String) : RelativeDayCategory

    data class Future(val mediumDate: String) : RelativeDayCategory
  }

  private fun classifyRelativeDay(
      instant: java.time.Instant,
      zoneId: ZoneId,
      locale: Locale,
  ): RelativeDayCategory {
    val targetZdt = instant.atZone(zoneId)
    val targetDate = targetZdt.toLocalDate()
    val nowDate = java.time.Instant.now().atZone(zoneId).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(targetDate, nowDate)

    val formatters = getFormatters(locale, zoneId)

    return when {
      daysBetween < 0 -> RelativeDayCategory.Future(formatters.mediumDate.format(targetZdt))

      daysBetween == 0L -> RelativeDayCategory.Today

      daysBetween == 1L -> RelativeDayCategory.Yesterday

      isSameWeek(targetDate, nowDate, locale) && daysBetween in 2..6 ->
          RelativeDayCategory.ThisWeek(formatters.weekday.format(targetZdt))

      targetDate.year == nowDate.year -> {
        val pattern = getBestPattern(locale, "MMMd")
        val monthDay =
            DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId).format(targetZdt)
        RelativeDayCategory.SameYear(monthDay)
      }

      else -> RelativeDayCategory.OtherYear(formatters.mediumDate.format(targetZdt))
    }
  }

  private fun getFormatters(locale: Locale, zoneId: ZoneId): CachedFormatters {
    val key = FormatterKey(locale, zoneId)
    return formatterCache.getOrPut(key) {
      CachedFormatters(
          shortTime =
              DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                  .withLocale(locale)
                  .withZone(zoneId),
          weekday = DateTimeFormatter.ofPattern("EEEE", locale),
          mediumDate =
              DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                  .withLocale(locale)
                  .withZone(zoneId),
      )
    }
  }

  private fun isSameWeek(date1: LocalDate, date2: LocalDate, locale: Locale): Boolean {
    val weekFields = WeekFields.of(locale)
    return date1.get(weekFields.weekOfWeekBasedYear()) ==
        date2.get(weekFields.weekOfWeekBasedYear()) &&
        date1.get(weekFields.weekBasedYear()) == date2.get(weekFields.weekBasedYear())
  }

  private fun getLocalizedYesterday(locale: Locale) =
      getIcuBasedLocalizedDay(RelativeDay.Yesterday, locale).getOrElse { "Yesterday" }

  private fun getLocalizedToday(locale: Locale): String =
      getIcuBasedLocalizedDay(RelativeDay.Today, locale).getOrElse { "Today" }

  private enum class RelativeDay {
    Yesterday,
    Today,
  }

  private fun getIcuBasedLocalizedDay(relativeDay: RelativeDay, locale: Locale) = runCatching {
    val direction =
        when (relativeDay) {
          RelativeDay.Yesterday -> android.icu.text.RelativeDateTimeFormatter.Direction.LAST
          RelativeDay.Today -> android.icu.text.RelativeDateTimeFormatter.Direction.THIS
        }
    val formatter = android.icu.text.RelativeDateTimeFormatter.getInstance(locale)
    val raw =
        formatter.format(
            direction,
            android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit.DAY,
        )
    raw.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(locale) else it.toString()
    }
  }

  private fun getBestPattern(locale: Locale, skeleton: String): String {
    return runCatching {
      android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
    }
        .getOrElse {
          if (locale.language == Locale.CHINESE.language) "M月d日" else "MMM d"
        }
  }
}
