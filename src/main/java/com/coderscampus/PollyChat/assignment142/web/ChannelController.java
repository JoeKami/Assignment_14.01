package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ChannelController {

    ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/channel/$channelId")
    public String channel(Long channelId) {

        return "channel";
    }
}
