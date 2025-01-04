package me.taromati.afreecatv.event.implement

import me.taromati.afreecatv.event.AfreecatvEvent

data class DonationChatEvent(
    val channelId: String,
    val userId: String? = null,
    val nickname: String,
    val message: String? = null,
    val payAmount: Int,
    val balloonAmount: Int,
) : AfreecatvEvent
