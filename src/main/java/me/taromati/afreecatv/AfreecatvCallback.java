package me.taromati.afreecatv;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
public class AfreecatvCallback {

    final String command;
    final List<String> dataList;
    final LocalDateTime receivedTime = LocalDateTime.now();

    public AfreecatvCallback(String[] args) {
        this.dataList = new ArrayList<>(Arrays.asList(args));
        String cmd = dataList.removeFirst();
        this.command = cmd.substring(0, 4);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Command: ").append(command).append("\n");
        sb.append("Data: ");
        for (String d : dataList) {
            sb.append(d).append(" ");
        }
        return sb.toString();
    }

}
