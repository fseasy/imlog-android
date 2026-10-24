package top.fseasy.imlog.features.home.topiclog.composer2.classic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.fseasy.imlog.domain.model.ScreenOrientation

class ClassicComposerUiStateHolder(
    private val currentCoroutineScope: CoroutineScope,
) {
  private val _dockUiState = MutableStateFlow(ComposerDockState())
  val dockUiState = _dockUiState.asStateFlow()

  init {
    currentCoroutineScope.launch {
      // 从本地加载持久化的高度（若无记录，则返回合理的各方向默认值）
      //      _cachedHeights.value = preferencesRepository.loadKeyboardHeights()
    }
  }

  fun toggleAttachmentPanel() {
    _dockUiState.update { s ->
      s.copy(
          activePanel =
              if (s.activePanel.isAttachmentActive()) ActivePanel.NONE else ActivePanel.ATTACHMENT
      )
    }
  }

  fun onTextFieldFocusChanged(focused: Boolean) {
    if (focused) {
      _dockUiState.update { s -> s.copy(activePanel = ActivePanel.NONE) }
    }
  }

  fun updateCachedImeHeight(orientation: ScreenOrientation, heightInDp: Float) {
    // SKIP Invalid value.
    if (heightInDp < 1e-6f) return
    val oldState = _dockUiState.getAndUpdate { s ->
      val currentHeight = s.cachedImeHeightInDp.get(orientation)
      if (currentHeight == heightInDp) {
        s
      } else {
        s.copy(cachedImeHeightInDp = s.cachedImeHeightInDp.with(orientation, heightInDp))
      }
    }
    val oldHeight = oldState.cachedImeHeightInDp.get(orientation)
    if (oldHeight != heightInDp) {
      currentCoroutineScope.launch {
        // Update DB
        //        preferencesRepository.saveKeyboardHeight(orientation, heightInDp)
      }
    }
  }
}
