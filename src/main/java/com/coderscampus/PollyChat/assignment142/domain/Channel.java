package com.coderscampus.PollyChat.assignment142.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.List;

@Entity
public class Channel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long channelId;

    @NotBlank(message = "Channel name is required")
    @Size(min = 3, max = 30, message = "Channel name must be between 3 and 30 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s_-]+$", message = "Channel name can only contain letters, numbers, spaces, underscores, and hyphens")
    private String channelName;

    @OneToMany(mappedBy = "channel", cascade = CascadeType.ALL)
    private List<Message> messages;

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

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}
