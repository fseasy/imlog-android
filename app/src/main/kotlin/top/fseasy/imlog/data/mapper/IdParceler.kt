package top.fseasy.imlog.data.mapper

import android.os.Parcel
import kotlinx.parcelize.Parceler
import top.fseasy.imlog.domain.model.MessageId
import top.fseasy.imlog.domain.model.TopicId
import top.fseasy.imlog.domain.model.UserId

object UserIdParceler : Parceler<UserId> {
  override fun create(parcel: Parcel): UserId = UserId(parcel.readString()!!)

  override fun UserId.write(parcel: Parcel, flags: Int) = parcel.writeString(this.value)
}

object MessageIdParceler : Parceler<MessageId> {
  override fun create(parcel: Parcel): MessageId = MessageId(parcel.readString()!!)

  override fun MessageId.write(parcel: Parcel, flags: Int) = parcel.writeString(this.value)
}

object TopicIdParceler : Parceler<TopicId> {
  override fun create(parcel: Parcel): TopicId = TopicId(parcel.readString()!!)

  override fun TopicId.write(parcel: Parcel, flags: Int) = parcel.writeString(value)
}
