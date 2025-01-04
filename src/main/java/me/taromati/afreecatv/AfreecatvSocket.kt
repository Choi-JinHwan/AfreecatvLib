package me.taromati.afreecatv

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import me.taromati.afreecatv.data.AfreecatvInfo
import me.taromati.afreecatv.event.AfreecatvEvent
import me.taromati.afreecatv.event.implement.DonationChatEvent
import me.taromati.afreecatv.event.implement.MessageChatEvent
import me.taromati.afreecatv.utility.SSLUtil
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake

class AfreecatvSocket(api: AfreecatvAPI, url: String, draft6455: Draft_6455?, info: AfreecatvInfo, channelId: String) :
    WebSocketClient(URI.create(url), draft6455) {
    private val F = "\u000c"
    private val ESC = "\u001b\t"

    private val KEY_ENTER_FAN = "0127"
    private val KEY_SUB = "0093"
    private val KEY_CHAT = "0005"
    private val KEY_DONE = "0018"
    private val KEY_PING = "0000"
    private val KEY_CONNECT = "0001"
    private val KEY_JOIN = "0002"
    private val KEY_ENTER = "0004"

    private val api: AfreecatvAPI
    private val info: AfreecatvInfo
    private val channelId: String

    private val CONNECT_PACKET = makePacket(KEY_CONNECT, String.format("%s16%s", F.repeat(3), F))
    private val CONNECT_RES_PACKET = makePacket(KEY_CONNECT, String.format("%s16|0%s", F.repeat(2), F))
    private val PING_PACKET = makePacket(KEY_PING, F)

    private var pingThread: Thread? = null
    private var isAlive = true
    private val packetMap: MutableMap<String, AfreecatvCallback> = HashMap()

    init {
        this.connectionLostTimeout = 0
        this.setSocketFactory(SSLUtil.createSSLSocketFactory())

        this.api = api
        this.info = info
        this.channelId = channelId
    }

    override fun onOpen(handshake: ServerHandshake) {
        isAlive = true
        pingThread = Thread {
            val connectPacketBytes: ByteArray =
                CONNECT_PACKET.toByteArray(StandardCharsets.UTF_8)
            send(connectPacketBytes)
            while (isAlive) {
                try {
                    Thread.sleep(59996)
                    val pingPacketBytes: ByteArray =
                        PING_PACKET.toByteArray(StandardCharsets.UTF_8)
                    send(pingPacketBytes)
                    for ((key, packet) in packetMap) {
                        if (packet.receivedTime.isBefore(LocalDateTime.now().minusMinutes(1))) {
                            packetMap.remove(key)
                        }
                    }
                } catch (ignore: InterruptedException) {
                }
            }
        }
        pingThread!!.start()
    }

    override fun onMessage(message: String) {}

    override fun onMessage(bytes: ByteBuffer) {
        val message = String(bytes.array(), StandardCharsets.UTF_8)
        if (CONNECT_RES_PACKET == message) {
            val CHATNO = info.channelNumber
            val JOIN_PACKET = makePacket(KEY_JOIN, String.format("%s%s%s", F, CHATNO, F.repeat(5)))
            val joinPacketBytes: ByteArray = JOIN_PACKET.toByteArray(StandardCharsets.UTF_8)
            send(joinPacketBytes)
            return
        }

        try {
            val callback = AfreecatvCallback(message.replace(ESC, "").split(F.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())

            val cmd = callback.command
            val dataList = when (cmd) {
                KEY_ENTER, KEY_ENTER_FAN -> null
                else -> callback.dataList
            }

            if (dataList == null) return

            var msg: String? = null
            var nickname: String? = null
            var payAmount = 0
            var balloonAmount = 0
            if (cmd == KEY_DONE) {
                packetMap[dataList[2]] = callback
            } else if (cmd == KEY_CHAT) {
                val nick = dataList[5]
                if (packetMap.containsKey(nick)) {
                    val doneCallback = packetMap.getOrDefault(nick, null) ?: return
                    packetMap.remove(nick)
                    msg = dataList[0]
                    nickname = doneCallback.dataList[2]
                    payAmount = doneCallback.dataList[3].toInt() * 100
                    balloonAmount = doneCallback.dataList[3].toInt()
                } else {
                    msg = dataList[0]
                    nickname = nick
                }
            } else if (cmd == KEY_SUB) {
                val nick = dataList[5]
                if (packetMap.containsKey(nick)) {
                    packetMap.remove(nick)
                }
            }

            if (nickname != null && msg != null) {
                msg = msg.ifEmpty { "없음" }
                if (payAmount > 0 && balloonAmount > 0) {
                    processChatMessage(DonationChatEvent(this.channelId, nickname, msg, payAmount, balloonAmount))
                } else {
                    processChatMessage(MessageChatEvent(this.channelId, nickname, msg))
                }
            }
        } catch (ignored: Exception) {
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        this.isAlive = false
        pingThread!!.interrupt()
        this.pingThread = null
    }

    override fun onError(e: Exception) {}

    private fun makePacket(command: String, data: String): String {
        return String.format("%s%s%s%s", ESC, command, makeLengthPacket(data), data)
    }

    private fun makeLengthPacket(data: String): String {
        return String.format("%06d00", data.length)
    }

    private fun processChatMessage(event: AfreecatvEvent) {
        for (listener in api.listeners) {
            if (event is DonationChatEvent) {
                listener.onDonationChat(event)
            } else if (event is MessageChatEvent) {
                listener.onMessageChat(event)
            }
        }
    }
}
