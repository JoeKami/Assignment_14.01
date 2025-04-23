package com.coderscampus.PollyChat.assignment142.domain;

import jakarta.persistence.*;

@Entity
public class Channel {
    private Long channelId;
    private String channelName;

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }
}
