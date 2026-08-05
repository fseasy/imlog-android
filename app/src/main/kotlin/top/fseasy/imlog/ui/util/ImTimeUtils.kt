package top.fseasy.imlog.ui.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for formatting timestamps in a WeChat / IM style. Formatters are cached for performance
 * when called frequently.
 */
object ImTimeUtils {

  // Cache: (locale + zoneId) → commonly used formatters
  private data class FormatterKey(val locale: Locale, val zoneId: ZoneId)

  private data class CachedFormatters(
      val shortTime: DateTimeFormatter,
      val weekday: DateTimeFormatter,
      val mediumDate: DateTimeFormatter,
  )

  private val formatterCache = ConcurrentHashMap<FormatterKey, CachedFormatters>()

  /**
   * Formats an [Instant] into a localized WeChat-style time string.
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
   */
  fun formatImTime(
      instant: java.time.Instant,
      zoneId: ZoneId = ZoneId.systemDefault(),
      locale: Locale = Locale.getDefault(),
  ): String {
    val targetZdt = instant.atZone(zoneId)
    val targetDate = targetZdt.toLocalDate()
    val nowDate = java.time.Instant.now().atZone(zoneId).toLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(targetDate, nowDate)

    val formatters = getFormatters(locale, zoneId)

    return when {
      daysBetween < 0 -> formatters.mediumDate.format(targetZdt) // future

      daysBetween == 0L -> formatters.shortTime.format(targetZdt) // today

      daysBetween == 1L -> getLocalizedYesterday(locale) // yesterday

      isSameWeek(targetDate, nowDate, locale) && daysBetween in 2..6 ->
          formatters.weekday.format(targetZdt) // this week

      targetDate.year == nowDate.year -> { // same year
        val pattern = getBestPattern(locale, "MMMd")
        DateTimeFormatter.ofPattern(pattern, locale).withZone(zoneId).format(targetZdt)
      }

      else -> formatters.mediumDate.format(targetZdt) // previous years
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

  private fun getLocalizedYesterday(locale: Locale): String {
    return runCatching {
      val formatter = android.icu.text.RelativeDateTimeFormatter.getInstance(locale)
      val raw =
          formatter.format(
              android.icu.text.RelativeDateTimeFormatter.Direction.LAST,
              android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit.DAY,
          )
      raw.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
      }
    }
        .getOrElse {
          if (locale.language == Locale.CHINESE.language) "昨天" else "Yesterday"
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
