package me.taromati.afreecatv

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import lombok.Getter
import me.taromati.afreecatv.data.AfreecatvInfo
import me.taromati.afreecatv.data.AfreecatvLiveInfo
import me.taromati.afreecatv.exception.AfreecatvException
import me.taromati.afreecatv.exception.ExceptionCode
import me.taromati.afreecatv.listener.AfreecatvListener
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.protocols.IProtocol
import org.java_websocket.protocols.Protocol
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

@Getter
class AfreecatvAPI(private var channelId: String?) {
    private var socket: AfreecatvSocket? = null

    val listeners: MutableList<AfreecatvListener> = ArrayList()

    fun connect(): AfreecatvAPI {
        if (!isConnected) {
            try {
                val info = getInfo(this.channelId)
                val draft6455 = Draft_6455(
                    emptyList(),
                    listOf<IProtocol>(Protocol("chat"))
                )
                val webSocket = AfreecatvSocket(
                    this,
                    "wss://" + info.channelDomain + ":" + info.channelPt + "/Websocket/" + info.streamerId,
                    draft6455,
                    info,
                    channelId!!
                )
                webSocket.connect()
                this.socket = webSocket
                return this
            } catch (e: Exception) {
                this.channelId = null
                this.socket = null
                return this
            }
        }
        return this
    }

    fun disconnect(): AfreecatvAPI {
        if (this.socket != null) {
            socket!!.close()
            this.socket = null
            this.channelId = null
        }
        return this
    }

    fun addListeners(listeners: List<AfreecatvListener>): AfreecatvAPI {
        this.listeners.addAll(listeners)
        return this
    }

    val isConnected: Boolean
        get() = socket != null && !socket!!.isClosed

    class AfreecatvBuilder {
        private var channelId: String? = null

        fun withData(channelId: String?): AfreecatvBuilder {
            this.channelId = channelId
            return this
        }

        fun build(): AfreecatvAPI {
            return createAPI(this.channelId)
        }
    }

    companion object {
        fun getLiveInfo(bjId: String): AfreecatvLiveInfo {
            try {
                val response = HttpClient.newHttpClient().use { client ->
                    val bodyJson = JSONObject(
                        mapOf(
                            "bid" to bjId,
                            "type" to "live",
                            "confirm_adult" to "false",
                            "player_type" to "html5",
                            "mode" to "landing",
                            "from_api" to "0",
                            "pwd" to "",
                            "stream_type" to "common",
                            "quality" to "HD",
                        )
                    )

                    val request = HttpRequest.newBuilder().POST(formData(bodyJson))
                        .uri(URI.create("https://live.afreecatv.com/afreeca/player_live_api.php?bjid=$bjId"))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                        )
                        .header("Content-Type", "application/x-www-form-urlencoded").build()
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                }
                if (response.statusCode() != 200) {
                    throw AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR)
                }
                val parser = JSONParser()
                val jsonObject = parser.parse(response.body()) as JSONObject
                val channel = jsonObject["CHANNEL"] as JSONObject
                val categoryTags: MutableList<String> = ArrayList()
                for (s in JSONParser().parse(channel["CATEGORY_TAGS"].toString()) as JSONArray) {
                    categoryTags.add(s.toString())
                }
                return AfreecatvLiveInfo(
                    channel["BJID"].toString(),
                    channel["BJNICK"].toString(),
                    channel["TITLE"].toString(),
                    categoryTags
                )
            } catch (e: Exception) {
                throw AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR)
            }
        }

        fun createAPI(channelId: String?): AfreecatvAPI {
            return AfreecatvAPI(channelId)
        }

        private fun getInfo(bjId: String?): AfreecatvInfo {
            try {
                val client = HttpClient.newHttpClient()
                val bodyJson = JSONObject(
                    mapOf(
                        "bid" to bjId,
                        "type" to "live",
                        "confirm_adult" to "false",
                        "player_type" to "html5",
                        "mode" to "landing",
                        "from_api" to "0",
                        "pwd" to "",
                        "stream_type" to "common",
                        "quality" to "HD",
                    )
                )
                val request = HttpRequest.newBuilder().POST(formData(bodyJson))
                    .uri(URI.create("https://live.afreecatv.com/afreeca/player_live_api.php?bjid=$bjId"))
                    .headers(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        "Content-Type", "application/x-www-form-urlencoded"
                    ).build()
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val parser = JSONParser()
                    val jsonObject = parser.parse(response.body()) as JSONObject
                    val channel = jsonObject["CHANNEL"] as JSONObject
                    return AfreecatvInfo(
                        channel["CHDOMAIN"].toString(),
                        channel["CHATNO"].toString(),
                        (channel["CHPT"].toString().toInt() + 1).toString(),
                        channel["FTK"].toString(),
                        channel["TITLE"].toString(),
                        channel["BJID"].toString(),
                        channel["BNO"].toString()
                    )
                } else {
                    throw AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR)
                }
            } catch (e: Exception) {
                throw AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR)
            }
        }

        private fun formData(data: JSONObject): BodyPublisher {
            val builder = StringBuilder()
            for ((key, value) in data) {
                builder.apply {
                    if (isNotEmpty()) {
                        append("&")
                    }
                    append(urlEncode(key.toString()))
                    append("=")
                    append(urlEncode(value.toString()))
                }
            }
            return HttpRequest.BodyPublishers.ofString(builder.toString())
        }

        private fun urlEncode(data: String) {
            URLEncoder.encode(data, StandardCharsets.UTF_8)
        }
    }
}
