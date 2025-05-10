package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.Message;
import com.coderscampus.PollyChat.assignment142.domain.User;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import com.coderscampus.PollyChat.assignment142.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChannelController {
    private static final Logger logger = LoggerFactory.getLogger(ChannelController.class);
    private final ChannelService channelService;
    private final UserService userService;

    public ChannelController(ChannelService channelService, UserService userService) {
        this.channelService = channelService;
        this.userService = userService;
    }

    @GetMapping("/channel/{channelId}")
    public String channel(@PathVariable Long channelId, 
                         @RequestParam(required = false) String windowId,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Accessing channel {} with windowId: {}", channelId, windowId);
            
            // If windowId is not provided, redirect to welcome page
            if (windowId == null) {
                logger.debug("No windowId provided, redirecting to welcome page");
                return "redirect:/welcome";
            }
            
            // Store the window ID in the session
            session.setAttribute("windowId", windowId);
            
            // Get the current channel
            var currentChannel = channelService.getChannelByChannelId(channelId);
            if (currentChannel == null) {
                logger.error("Channel not found with ID: {}", channelId);
                redirectAttributes.addFlashAttribute("error", "Channel not found");
                return "redirect:/welcome?windowId=" + windowId;
            }
            model.addAttribute("currentChannel", currentChannel);
            
            // Get all channels for the sidebar
            model.addAttribute("channels", channelService.getAllChannels());
            
            // Get the current user from the window-specific session
            String username = (String) session.getAttribute("username_" + windowId);
            logger.debug("Retrieved username for windowId {}: {}", windowId, username);
            
            if (username != null) {
                User currentUser = userService.getUserByUsername(username);
                if (currentUser == null) {
                    logger.error("User not found for username: {}", username);
                    session.removeAttribute("username_" + windowId);
                    redirectAttributes.addFlashAttribute("error", "User session expired");
                    return "redirect:/welcome?windowId=" + windowId;
                }
                model.addAttribute("currentUser", currentUser);
            } else {
                logger.debug("No user logged in for windowId: {}", windowId);
                redirectAttributes.addFlashAttribute("error", "Please sign in to access the channel");
                return "redirect:/welcome?windowId=" + windowId;
            }
            
            // Get messages for the channel
            model.addAttribute("messages", channelService.getMessagesForChannel(channelId));
            model.addAttribute("message", new Message());
            model.addAttribute("windowId", windowId);
            
            return "chat-channel";
        } catch (Exception e) {
            logger.error("Error accessing channel {}: {}", channelId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "An error occurred while accessing the channel");
            return "redirect:/welcome?windowId=" + windowId;
        }
    }

    @PostMapping("/channel/{channelId}/send")
    public String sendMessage(@PathVariable Long channelId,
                            @RequestParam String windowId,
                            @ModelAttribute Message message,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Attempting to send message to channel {} from windowId: {}", channelId, windowId);
            
            // Get the current user from the window-specific session
            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.error("No user logged in for windowId: {}", windowId);
                redirectAttributes.addFlashAttribute("error", "Please sign in to send messages");
                return "redirect:/welcome?windowId=" + windowId;
            }

            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.error("User not found for username: {}", username);
                session.removeAttribute("username_" + windowId);
                redirectAttributes.addFlashAttribute("error", "User session expired");
                return "redirect:/welcome?windowId=" + windowId;
            }

            message.setUser(currentUser);
            channelService.sendMessage(channelId, message);
            logger.debug("Message sent successfully to channel {}", channelId);
            
            return "redirect:/channel/" + channelId + "?windowId=" + windowId;
        } catch (Exception e) {
            logger.error("Error sending message to channel {}: {}", channelId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to send message");
            return "redirect:/channel/" + channelId + "?windowId=" + windowId;
        }
    }

    @PostMapping("/channel/{channelId}/delete")
    public String deleteChannel(@PathVariable Long channelId,
                              @RequestParam String windowId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Attempting to delete channel {} from windowId: {}", channelId, windowId);
            
            // Verify user is logged in
            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.error("No user logged in for windowId: {}", windowId);
                redirectAttributes.addFlashAttribute("error", "Please sign in to delete channels");
                return "redirect:/welcome?windowId=" + windowId;
            }

            // Delete the channel and its messages
            channelService.deleteChannel(channelId);
            logger.debug("Channel {} and its messages deleted successfully", channelId);
            
            redirectAttributes.addFlashAttribute("success", "Channel deleted successfully");
            return "redirect:/welcome?windowId=" + windowId;
        } catch (ChangeSetPersister.NotFoundException e) {
            logger.error("Channel not found with ID: {}", channelId);
            redirectAttributes.addFlashAttribute("error", "Channel not found");
            return "redirect:/welcome?windowId=" + windowId;
        } catch (Exception e) {
            logger.error("Error deleting channel {}: {}", channelId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete channel: " + e.getMessage());
            return "redirect:/welcome?windowId=" + windowId;
        }
    }

    @PostMapping("/channel/{channelId}/edit")
    public String editChannel(@PathVariable Long channelId,
                            @RequestParam String windowId,
                            @RequestParam String newChannelName,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Attempting to edit channel {} from windowId: {}", channelId, windowId);
            
            // Verify user is logged in
            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.error("No user logged in for windowId: {}", windowId);
                redirectAttributes.addFlashAttribute("error", "Please sign in to edit channels");
                return "redirect:/welcome?windowId=" + windowId;
            }

            // Validate channel name
            if (newChannelName == null || newChannelName.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Channel name cannot be empty");
                return "redirect:/channel/" + channelId + "?windowId=" + windowId;
            }

            // Get and update the channel
            Channel channel = channelService.getChannelByChannelId(channelId);
            channel.setChannelName(newChannelName.trim());
            channelService.createChannel(channel);
            
            logger.debug("Channel {} edited successfully", channelId);
            redirectAttributes.addFlashAttribute("success", "Channel name updated successfully");
            return "redirect:/channel/" + channelId + "?windowId=" + windowId;
        } catch (ChangeSetPersister.NotFoundException e) {
            logger.error("Channel not found with ID: {}", channelId);
            redirectAttributes.addFlashAttribute("error", "Channel not found");
            return "redirect:/welcome?windowId=" + windowId;
        } catch (Exception e) {
            logger.error("Error editing channel {}: {}", channelId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to edit channel: " + e.getMessage());
            return "redirect:/channel/" + channelId + "?windowId=" + windowId;
        }
    }
}
