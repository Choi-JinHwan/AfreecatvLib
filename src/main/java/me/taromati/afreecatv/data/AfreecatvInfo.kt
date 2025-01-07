package me.taromati.afreecatv.data

data class AfreecatvInfo(
    val channelDomain: String? = null,
    val channelNumber: String? = null,
    val channelPt: String? = null,
    val streamerFtk: String? = null,
    val streamerTitle: String? = null,
    val streamerId: String? = null,
    val streamerNo: String? = null,
) {
    val isLive: Boolean
        get() = this.channelDomain != null
}
