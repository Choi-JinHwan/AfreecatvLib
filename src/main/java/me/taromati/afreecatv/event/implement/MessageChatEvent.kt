package me.taromati.afreecatv.event.implement

import lombok.AllArgsConstructor
import lombok.Getter
import me.taromati.afreecatv.event.AfreecatvEvent

@Getter
@AllArgsConstructor
class MessageChatEvent(
    val channelId: String? = null,
    val nickname: String? = null,
    val message: String? = null,
) : AfreecatvEvent
