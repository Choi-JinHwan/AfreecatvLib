@file:Suppress("PrivatePropertyName")

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
    private val packetMap: MutableMap<String, AfreecatvCallback> = mutableMapOf()

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
                CONNECT_PACKET.toByteArray()
            send(connectPacketBytes)
            while (isAlive) {
                try {
                    Thread.sleep(59996)
                    val pingPacketBytes: ByteArray =
                        PING_PACKET.toByteArray()
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
        val message = bytes.array().toString(StandardCharsets.UTF_8)
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

            dataList ?: return

            var msg: String? = null
            var nickname: String? = null
            var userId: String? = null
            var payAmount = 0
            var balloonAmount = 0
//            마지막 -1은 구독?이 아닌 경우인듯
//          까비 1개 도네
//          	000500006500까비eqaz1366003eqaz136665568|163840-10B6D826CABCF-1
//          저녁은 좀 2개 도네
//          	000500007200저격은 좀eqaz1366003eqaz136665568|163840-10B6D826CABCF-1
//          쿨도네 1개
//          	001800006800jooinvlupeqaz1366eqaz136610062351_0004001900kor_custom04
//          쿨도네 3개
//          	001800005900jooinvlupeqaz1366eqaz13663006235300kor_custom13
            when  {
                cmd == KEY_DONE && dataList[0] != channelId -> {
                    packetMap[dataList[2]] = callback
                }
                // 쿨도네
                cmd == KEY_DONE && dataList[0] == channelId -> {
                    msg = null
                    userId = dataList[1]
                    nickname = dataList[2]
                    payAmount = dataList[3].toInt() * 100
                    balloonAmount = dataList[3].toInt()
                }
                cmd == KEY_CHAT -> {
                    val nick = dataList[5]
                    if (packetMap.containsKey(nick)) {
                        val doneCallback = packetMap.getOrDefault(nick, null) ?: return
                        packetMap.remove(nick)
                        msg = dataList[0]
                        userId = dataList[1]
                        nickname = doneCallback.dataList[2]
                        payAmount = doneCallback.dataList[3].toInt() * 100
                        balloonAmount = doneCallback.dataList[3].toInt()
                    } else {
                        userId = dataList[1]
                        msg = dataList[0]
                        nickname = nick
                    }
                }
                cmd == KEY_SUB -> {
                    val nick = dataList[5]
                    if (packetMap.containsKey(nick)) {
                        packetMap.remove(nick)
                    }
                }
            }

            nickname ?: return

            val event = when {
                payAmount > 0 && balloonAmount > 0 -> {
                    DonationChatEvent(this.channelId, userId, nickname, msg, payAmount, balloonAmount)
                }
                msg == null -> return
                else -> {
                    MessageChatEvent(this.channelId, nickname, msg)
                }
            }
            processChatMessage(event)
        } catch (ignored: Exception) {
        }
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) {
        this.isAlive = false
        pingThread?.interrupt()
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
        when (event) {
            is DonationChatEvent -> {
                api.onDonationChat(event)
            }

            is MessageChatEvent -> {
                api.onMessageChat(event)
            }
        }
    }
}
