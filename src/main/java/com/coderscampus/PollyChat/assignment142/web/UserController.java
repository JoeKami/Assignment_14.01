package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.*;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import com.coderscampus.PollyChat.assignment142.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Controller
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    private final ChannelService channelService;

    public UserController(UserService userService, ChannelService channelService) {
        this.userService = userService;
        this.channelService = channelService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/welcome";
    }

    @GetMapping("/welcome")
    public String welcome(Model model, HttpSession session, @RequestParam(required = false) String windowId) {
        // Generate a new window ID if one doesn't exist
        if (windowId == null) {
            windowId = UUID.randomUUID().toString();
            return "redirect:/welcome?windowId=" + windowId;
        }
        
        // Store the window ID in the session
        session.setAttribute("windowId", windowId);
        
        model.addAttribute("user", new User());
        model.addAttribute("channel", new Channel());
        model.addAttribute("channels", channelService.getAllChannels());
        model.addAttribute("windowId", windowId);

        // Get username specific to this window
        String windowUsername = (String) session.getAttribute("username_" + windowId);
        if (windowUsername != null) {
            model.addAttribute("username", windowUsername);
        }
        return "welcome";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, 
                             HttpSession session,
                             @RequestParam String windowId) {
        // This will either create a new user or return an existing one
        User existingOrNewUser = userService.createUser(user);
        logger.debug("User registered/retrieved: {} with ID: {}", 
                    existingOrNewUser.getUsername(), 
                    existingOrNewUser.getUserId());
        
        // Store username with window-specific key
        session.setAttribute("username_" + windowId, existingOrNewUser.getUsername());
        return "redirect:/welcome?windowId=" + windowId;
    }

    @PostMapping("/create-channel")
    public String registerChannel(@ModelAttribute("channel") Channel channel,
                                @RequestParam String windowId) {
        channelService.createChannel(channel);
        return "redirect:/welcome?windowId=" + windowId;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, @RequestParam String windowId) {
        // Only remove the window-specific username
        session.removeAttribute("username_" + windowId);
        return "redirect:/welcome?windowId=" + windowId;
    }
}
