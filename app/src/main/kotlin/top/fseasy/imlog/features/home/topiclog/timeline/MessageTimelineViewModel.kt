package top.fseasy.imlog.features.home.topiclog.timeline

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import top.fseasy.imlog.domain.model.AuthState
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.repository.MessageRepository
import top.fseasy.imlog.domain.repository.UserRepository
import top.fseasy.imlog.domain.usecase.StoragePathUseCase
import top.fseasy.imlog.navigation.MainScreen
import top.fseasy.imlog.ui.util.ImTimeUtils
import top.fseasy.imlog.ui.util.toJavaInstant
import top.fseasy.imlog.ui.util.toLocaleEpochDays
import javax.inject.Inject

@HiltViewModel
class MessageTimelineViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val storagePathUseCase: StoragePathUseCase,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
  val topicId: TopicId = TopicId(savedStateHandle.toRoute<MainScreen.TopicLog>().topicId)

  // Item is sorted in DESC order => in Timeline LazyColumn view, should be rendered reversely.
  @OptIn(ExperimentalCoroutinesApi::class)
  val pagedMessagesStateFlow: Flow<PagingData<TimelineItemUiModel>> =
      userRepository.authState.filterIsInstance<AuthState.Authenticated>().flatMapLatest { state ->
        messageRepository
            .pagedTopicMessages(topicId)
            .map { pagingTimelineMessage ->
              pagingTimelineMessage.map { timelineMessage ->
                val messageUiModel =
                    timelineMessage.toUiModel(
                        signInUserId = state.userId,
                        topicId = topicId,
                        storagePathUseCase = storagePathUseCase,
                        context = context,
                    )
                // NOTE: needs the `as` to cast to the base type, or will get error
                TimelineItemUiModel.MessageItem(messageUiModel) as TimelineItemUiModel
              }
            }
            .map { pagingMessageUiModel ->
              pagingMessageUiModel.insertSeparators { newer, older ->
                pagingDataInsertSeparators(newerMessageItem = newer, olderMessageItem = older)
              }
            }
            .cachedIn(viewModelScope)
      }
}

/**
 * @param newerMessage the before item of this paging. if it's null, means the [olderMessage] is the
 *   first item of this paging
 * @param olderMessage the after item of this paging. if it's null, means the [newerMessage] is the
 *   last item of this paging
 *
 * NOTE1: demonstrate the logic:
 *
 * - paging data: 1, 2, 3
 * - insertSeparator each inputs:
 *     - before = null, after = 1 => null, 1
 *     - before = 1, after = 2 => 1, <date-sep if same-day>, 2
 *     - before = 2, after = 3 => 2, <date-sep if same-day>, 3
 *     - before = 3, after = null => 3, <date-sep>, null
 *
 * NOTE 2: as the doc:
 *
 * > Separators are recomputed as new pages are loaded. Separators placed at terminal boundaries
 * > (where before or after is null) are temporary and will be re-evaluated when adjacent pages
 * > load.
 *
 * So don't worry about any weird separator occurs after new paging data loaded
 */
private fun pagingDataInsertSeparators(
    newerMessageItem: TimelineItemUiModel?,
    olderMessageItem: TimelineItemUiModel?,
): TimelineItemUiModel? {
  val newerMessage = (newerMessageItem as? TimelineItemUiModel.MessageItem)?.message
  val olderMessage = (olderMessageItem as? TimelineItemUiModel.MessageItem)?.message
  // olderMessage is the first item of the paging. No separator
  if (newerMessage == null) return null
  // newerMessage is the last item of the paging. Always insert a separator
  if (olderMessage == null) {
    val keyInstant = newerMessage.createdAt
    return TimelineItemUiModel.DateSeparator(
        separatingDateMs = keyInstant.toEpochMilliseconds(),
        formatedText = ImTimeUtils.formatImDay(keyInstant.toJavaInstant()),
    )
  }
  val newerMessageDay = newerMessage.createdAt.toLocaleEpochDays()
  val olderMessageDay = olderMessage.createdAt.toLocaleEpochDays()
  if (newerMessageDay != olderMessageDay) {
    // Not different day. Insert a separator (on newer message)
    val keyInstant = newerMessage.createdAt
    return TimelineItemUiModel.DateSeparator(
        separatingDateMs = keyInstant.toEpochMilliseconds(),
        formatedText = ImTimeUtils.formatImDay(keyInstant.toJavaInstant()),
    )
  }
  // same day. no separator
  return null
}
