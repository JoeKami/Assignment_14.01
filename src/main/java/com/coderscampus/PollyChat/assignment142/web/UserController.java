package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.User;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import com.coderscampus.PollyChat.assignment142.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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

    @GetMapping({"/", "/welcome"})
    public String welcome(Model model, HttpSession session, @RequestParam(required = false) String windowId) {
        if (windowId == null) {
            windowId = UUID.randomUUID().toString();
            return "redirect:/welcome?windowId=" + windowId;
        }
        
        session.setAttribute("windowId", windowId);
        model.addAttribute("user", new User());
        model.addAttribute("channel", new Channel());
        model.addAttribute("channels", channelService.getAllChannels());
        model.addAttribute("windowId", windowId);

        String windowUsername = (String) session.getAttribute("username_" + windowId);
        if (windowUsername != null) {
            model.addAttribute("username", windowUsername);
        }
        return "welcome";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user,
                             BindingResult bindingResult,
                             HttpSession session,
                             @RequestParam String windowId,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            bindingResult.getAllErrors().forEach(error -> {
                logger.error("Validation error: {}", error.getDefaultMessage());
                redirectAttributes.addFlashAttribute("error", error.getDefaultMessage());
            });
            return "redirect:/welcome?windowId=" + windowId;
        }

        try {
            User existingOrNewUser = userService.createUser(user);
            session.setAttribute("username_" + windowId, existingOrNewUser.getUsername());
            redirectAttributes.addFlashAttribute("success", "Successfully signed in as " + existingOrNewUser.getUsername());
            return "redirect:/welcome?windowId=" + windowId;
        } catch (Exception e) {
            logger.error("Error registering user: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to register user: " + e.getMessage());
            return "redirect:/welcome?windowId=" + windowId;
        }
    }

    @PostMapping("/create-channel")
    public String registerChannel(@ModelAttribute("channel") Channel channel,
                                @RequestParam String windowId) {
        channelService.createChannel(channel);
        return "redirect:/welcome?windowId=" + windowId;
    }

    @PostMapping("/logout")
    public String logout(@RequestParam String windowId, 
                        HttpSession session, 
                        RedirectAttributes redirectAttributes) {
        try {
            session.removeAttribute("username_" + windowId);
            redirectAttributes.addFlashAttribute("success", "Successfully logged out");
            return "redirect:/welcome?windowId=" + windowId;
        } catch (Exception e) {
            logger.error("Error during logout: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error during logout");
            return "redirect:/welcome?windowId=" + windowId;
        }
    }
}
