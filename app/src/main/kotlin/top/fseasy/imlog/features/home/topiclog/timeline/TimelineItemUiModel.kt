package top.fseasy.imlog.features.home.topiclog.timeline

sealed interface TimelineItemUiModel {
  val key: String

  data class MessageItem(val message: MessageUiModel) : TimelineItemUiModel {
    override val key: String
      get() = message.id.value
  }

  /**
   * Date separator in a timeline.
   *
   * @property separatingDateMs The absolute timestamp in milliseconds used as the unique and stable
   *   identifier.
   *
   * The key is based on [separatingDateMs] rather than [formatedText] to ensure stability, since
   * the formatted text can change in recomposing while the underlying timestamp remains the same.
   */
  data class DateSeparator(
      val separatingDateMs: Long,
      val formatedText: String,
  ) : TimelineItemUiModel {
    override val key: String
      get() = "ds_$separatingDateMs"
  }
}
