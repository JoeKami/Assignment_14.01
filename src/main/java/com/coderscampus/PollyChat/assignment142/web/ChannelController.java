package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.Message;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping("/channel/{channelId}")
    public String channel(@PathVariable Long channelId, Model model) throws ChangeSetPersister.NotFoundException {
        model.addAttribute("channel", channelService.getChannelByChannelId(channelId));
        model.addAttribute("message", new Message());
        return "channel/" + channelId;
    }

    @PostMapping("/channel/{channelId}")
    public String sendMessage(@PathVariable Long channelId, @ModelAttribute Message message) throws ChangeSetPersister.NotFoundException {
        channelService.sendMessage(channelId, message);
        return "redirect:/channel/" + channelId;
    }

    public String deleteChannel(@PathVariable Long channelId) throws ChangeSetPersister.NotFoundException {
        channelService.deleteChannel(channelId);
        return "redirect:/channel-list";
    }
}
