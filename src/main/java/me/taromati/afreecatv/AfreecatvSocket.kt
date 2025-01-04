package me.taromati.afreecatv;

import me.taromati.afreecatv.data.AfreecatvInfo;
import me.taromati.afreecatv.event.AfreecatvEvent;
import me.taromati.afreecatv.event.implement.DonationChatEvent;
import me.taromati.afreecatv.event.implement.MessageChatEvent;
import me.taromati.afreecatv.listener.AfreecatvListener;
import me.taromati.afreecatv.utility.SSLUtil;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AfreecatvSocket extends WebSocketClient {

    private final String F = "\u000c";
    private final String ESC = "\u001b\t";

    private final String KEY_ENTER_FAN = "0127";
    private final String KEY_SUB = "0093";
    private final String KEY_CHAT = "0005";
    private final String KEY_DONE = "0018";
    private final String KEY_PING = "0000";
    private final String KEY_CONNECT = "0001";
    private final String KEY_JOIN = "0002";
    private final String KEY_ENTER = "0004";

    private final AfreecatvAPI api;
    private final AfreecatvInfo info;
    private final String channelId;

    private final String CONNECT_PACKET = makePacket(KEY_CONNECT, String.format("%s16%s", F.repeat(3), F));
    private final String CONNECT_RES_PACKET = makePacket(KEY_CONNECT, String.format("%s16|0%s", F.repeat(2), F));
    private final String PING_PACKET = makePacket(KEY_PING, F);

    private Thread pingThread;
    private boolean isAlive = true;
    private Map<String, AfreecatvCallback> packetMap = new HashMap<>();

    public AfreecatvSocket(final AfreecatvAPI api, final String url, final Draft_6455 draft6455, final AfreecatvInfo info, final String channelId) {
        super(URI.create(url), draft6455);
        this.setConnectionLostTimeout(0);
        this.setSocketFactory(SSLUtil.createSSLSocketFactory());

        this.api = api;
        this.info = info;
        this.channelId = channelId;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        isAlive = true;
        pingThread = new Thread(() -> {
            byte[] connectPacketBytes = CONNECT_PACKET.getBytes(StandardCharsets.UTF_8);
            send(connectPacketBytes);
            while (isAlive) {
                try {
                    Thread.sleep(59996);
                    byte[] pingPacketBytes = PING_PACKET.getBytes(StandardCharsets.UTF_8);
                    send(pingPacketBytes);
                    for (Map.Entry<String, AfreecatvCallback> entry : packetMap.entrySet()) {
                        AfreecatvCallback packet = entry.getValue();
                        if (packet.getReceivedTime().isBefore(LocalDateTime.now().minusMinutes(1))) {
                            packetMap.remove(entry.getKey());
                        }
                    }
                } catch (InterruptedException ignore) { }
            }
        });
        pingThread.start();
    }

    @Override
    public void onMessage(String message) { }

    @Override
    public void onMessage(ByteBuffer bytes) {
        String message = new String(bytes.array(), StandardCharsets.UTF_8);
        if (CONNECT_RES_PACKET.equals(message)) {
            String CHATNO = this.info.getChannelNumber();
            String JOIN_PACKET = makePacket(KEY_JOIN, String.format("%s%s%s", F, CHATNO, F.repeat(5)));
            byte[] joinPacketBytes = JOIN_PACKET.getBytes(StandardCharsets.UTF_8);
            send(joinPacketBytes);
            return;
        }

        try {
            AfreecatvCallback callback = new AfreecatvCallback(message.replace(ESC, "").split(F));

            String cmd = callback.getCommand();
            List<String> dataList = switch (cmd) {
                case KEY_ENTER, KEY_ENTER_FAN -> null;
                default -> callback.getDataList();
            };

            if (dataList == null) return;

            String msg = null;
            String nickname = null;
            int payAmount = 0;
            int balloonAmount = 0;
            if (cmd.equals(KEY_DONE)) {
                packetMap.put(dataList.get(2), callback);
            } else if (cmd.equals(KEY_CHAT)) {
                String nick = dataList.get(5);
                if (packetMap.containsKey(nick)) {
                    AfreecatvCallback doneCallback = packetMap.get(nick);
                    packetMap.remove(nick);
                    msg = dataList.get(0);
                    nickname = doneCallback.getDataList().get(2);
                    payAmount = Integer.parseInt(doneCallback.getDataList().get(3)) * 100;
                    balloonAmount = Integer.parseInt(doneCallback.getDataList().get(3));
                } else {
                    msg = dataList.get(0);
                    nickname = nick;
                }
            } else if (cmd.equals(KEY_SUB)) {
                String nick = dataList.get(5);
                if (packetMap.containsKey(nick)) {
                    AfreecatvCallback doneCallback = packetMap.get(nick);
                    packetMap.remove(nick);
                }
            }

            if (nickname != null && msg != null) {
                msg = msg.isEmpty() ? "없음" : msg;
                if (payAmount > 0 && balloonAmount > 0) {
                    processChatMessage(new DonationChatEvent(this.channelId, nickname, msg, payAmount, balloonAmount));
                } else {
                    processChatMessage(new MessageChatEvent(this.channelId, nickname, msg));
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.isAlive = false;
        this.pingThread.interrupt();
        this.pingThread = null;
    }

    @Override
    public void onError(Exception e) { }

    private String makePacket(String command, String data) {
        return String.format("%s%s%s%s", ESC, command, makeLengthPacket(data), data);
    }

    private String makeLengthPacket(String data) {
        return String.format("%06d00", data.length());
    }

    private void processChatMessage(final AfreecatvEvent event) {
        for (final AfreecatvListener listener : this.api.getListeners()) {
            if (event instanceof DonationChatEvent e) {
                listener.onDonationChat(e);
            } else if (event instanceof MessageChatEvent e) {
                listener.onMessageChat(e);
            }
        }
    }

}