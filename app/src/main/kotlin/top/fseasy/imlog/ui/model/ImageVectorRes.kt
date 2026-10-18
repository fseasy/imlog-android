package top.fseasy.imlog.ui.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

/** Use this in non-composable context */
sealed interface ImageVectorRes {
  data class Vector(val imageVector: ImageVector) : ImageVectorRes

  data class ResId(@DrawableRes val id: Int) : ImageVectorRes
}

@Composable
fun ImageVectorRes.toImageVector() =
    when (this) {
      is ImageVectorRes.ResId -> ImageVector.vectorResource(this.id)
      is ImageVectorRes.Vector -> this.imageVector
    }
