package me.taromati.afreecatv.event.implement;

import me.taromati.afreecatv.event.AfreecatvEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DonationChatEvent implements AfreecatvEvent {

    private final String channelId;

    private final String nickname;
    private final String message;

    private final int payAmount;
    private final int balloonAmount;

}