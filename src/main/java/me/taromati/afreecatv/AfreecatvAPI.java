package me.taromati.afreecatv;

import me.taromati.afreecatv.data.AfreecatvInfo;
import me.taromati.afreecatv.data.AfreecatvLiveInfo;
import me.taromati.afreecatv.exception.AfreecatvException;
import me.taromati.afreecatv.exception.ExceptionCode;
import me.taromati.afreecatv.listener.AfreecatvListener;
import lombok.Getter;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.protocols.Protocol;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
public class AfreecatvAPI {

    private String channelId;
    private AfreecatvSocket socket;

    public final List<AfreecatvListener> listeners = new ArrayList<>();

    public AfreecatvAPI(final String channelId) {
        this.channelId = channelId;
    }

    public AfreecatvAPI connect() {
        if (!isConnected()) {
            try {
                AfreecatvInfo info = getInfo(this.channelId);
                Draft_6455 draft6455 = new Draft_6455(
                        Collections.emptyList(),
                        Collections.singletonList(new Protocol("chat"))
                );
                AfreecatvSocket webSocket = new AfreecatvSocket(this, "wss://" + info.getChannelDomain() + ":" + info.getChannelPt() + "/Websocket/" + info.getStreamerId(), draft6455, info, this.channelId);
                webSocket.connect();
                this.socket = webSocket;
                return this;
            } catch (Exception e) {
                this.channelId = null;
                this.socket = null;
                return this;
            }
        }
        return this;
    }

    public AfreecatvAPI disconnect() {
        if (this.socket != null) {
            this.socket.close();
            this.socket = null;
            this.channelId = null;
        }
        return this;
    }

    public AfreecatvAPI addListeners(final AfreecatvListener... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
        return this;
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    public static AfreecatvLiveInfo getLiveInfo(final String bjId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("bid", bjId);
            bodyJson.put("type", "live");
            bodyJson.put("confirm_adult", "false");
            bodyJson.put("player_type", "html5");
            bodyJson.put("mode", "landing");
            bodyJson.put("from_api", "0");
            bodyJson.put("pwd", "");
            bodyJson.put("stream_type", "common");
            bodyJson.put("quality", "HD");
            HttpRequest request = HttpRequest.newBuilder().POST(formData(bodyJson))
                    .uri(URI.create("https://live.afreecatv.com/afreeca/player_live_api.php?bjid=" + bjId))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded").build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());
                JSONObject channel = (JSONObject) jsonObject.get("CHANNEL");
                List<String> categoryTags = new ArrayList<>();
                for (final Object s : (JSONArray) new JSONParser().parse(channel.get("CATEGORY_TAGS").toString())) {
                    categoryTags.add(s.toString());
                }
                return new AfreecatvLiveInfo(
                        channel.get("BJID").toString(),
                        channel.get("BJNICK").toString(),
                        channel.get("TITLE").toString(),
                        categoryTags
                );
            } else {
                throw new AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
            }
        } catch (Exception e) {
            throw new AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
        }
    }

    public static AfreecatvAPI createAPI(final String channelId) {
        return new AfreecatvAPI(channelId);
    }

    public static class AfreecatvBuilder {

        private String channelId;

        public AfreecatvBuilder withData(final String channelId) {
            this.channelId = channelId;
            return this;
        }

        public AfreecatvAPI build() {
            return AfreecatvAPI.createAPI(this.channelId);
        }

    }

    private static AfreecatvInfo getInfo(String bjId) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("bid", bjId);
            bodyJson.put("type", "live");
            bodyJson.put("confirm_adult", "false");
            bodyJson.put("player_type", "html5");
            bodyJson.put("mode", "landing");
            bodyJson.put("from_api", "0");
            bodyJson.put("pwd", "");
            bodyJson.put("stream_type", "common");
            bodyJson.put("quality", "HD");
            HttpRequest request = HttpRequest.newBuilder().POST(formData(bodyJson))
                    .uri(URI.create("https://live.afreecatv.com/afreeca/player_live_api.php?bjid=" + bjId))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Content-Type", "application/x-www-form-urlencoded").build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(response.body());
                JSONObject channel = (JSONObject) jsonObject.get("CHANNEL");
                return new AfreecatvInfo(
                        channel.get("CHDOMAIN").toString(),
                        channel.get("CHATNO").toString(),
                        String.valueOf(Integer.parseInt(channel.get("CHPT").toString()) + 1),
                        channel.get("FTK").toString(),
                        channel.get("TITLE").toString(),
                        channel.get("BJID").toString(),
                        channel.get("BNO").toString()
                );
            } else {
                throw new AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
            }
        } catch (Exception e) {
            throw new AfreecatvException(ExceptionCode.API_CHAT_CHANNEL_ID_ERROR);
        }
    }

    private static HttpRequest.BodyPublisher formData(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append("&");
            }

            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

}
