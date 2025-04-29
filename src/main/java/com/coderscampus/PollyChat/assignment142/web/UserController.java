package com.coderscampus.PollyChat.assignment142.web;


import com.coderscampus.PollyChat.assignment142.domain.*;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import com.coderscampus.PollyChat.assignment142.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    private final UserService userService;
    private final ChannelService channelService;

    public UserController(UserService userService, ChannelService channelService) {
        this.userService = userService;
        this.channelService = channelService;
    }

    @GetMapping("/welcome")
    public String welcome(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("channel", new Channel());
        model.addAttribute("channels", channelService.getAllChannels());
        return "welcome";
    }

    @PostMapping("/welcome")
    public String registerUser(@ModelAttribute User user) {
        userService.createUser(user);
        return "welcome";
    }

    @PostMapping("/welcome")
    public String registerChannel(@ModelAttribute Channel channel) {
        channelService.createChannel(channel);
        return "chat-channel";
    }

}
