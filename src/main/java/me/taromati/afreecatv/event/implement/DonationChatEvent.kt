package me.taromati.afreecatv.event.implement

import me.taromati.afreecatv.event.AfreecatvEvent

data class DonationChatEvent(
    val channelId: String? = null,
    val userId: String? = null,
    val nickname: String? = null,
    val message: String? = null,
    val payAmount: Int?,
    val balloonAmount: Int?,
) : AfreecatvEvent
