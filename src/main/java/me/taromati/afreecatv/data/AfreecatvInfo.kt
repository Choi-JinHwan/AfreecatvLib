package me.taromati.afreecatv.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AfreecatvInfo {

    private final String channelDomain;
    private final String channelNumber;
    private final String channelPt;

    private final String streamerFtk;
    private final String streamerTitle;
    private final String streamerId;
    private final String streamerNo;

}