package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.Message;
import com.coderscampus.PollyChat.assignment142.domain.User;
import com.coderscampus.PollyChat.assignment142.service.ChannelService;
import com.coderscampus.PollyChat.assignment142.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.data.crossstore.ChangeSetPersister.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

@Controller
public class ChannelController {
    private static final Logger logger = LoggerFactory.getLogger(ChannelController.class);
    private final ChannelService channelService;
    private final UserService userService;
    
    // Store typing status per channel
    private final Map<Long, Set<String>> typingUsersByChannel = new ConcurrentHashMap<>();
    // Store session IDs per window to prevent session refresh
    private final Map<String, String> windowSessionMap = new ConcurrentHashMap<>();
    // Store active SSE emitters for each channel
    private final Map<Long, Set<SseEmitter>> channelEmitters = new ConcurrentHashMap<>();
    // Store active users per channel with their last activity time
    private final Map<Long, Map<String, Long>> activeUsersByChannel = new ConcurrentHashMap<>();
    private static final long USER_ACTIVITY_TIMEOUT = 30000; // 30 seconds

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
            
            if (windowId == null || windowId.trim().isEmpty()) {
                logger.debug("No windowId provided, redirecting to error page");
                return "error/no-window-id";
            }

            // Store the window ID in the session if not already present
            String currentSessionId = session.getId();
            String storedSessionId = windowSessionMap.get(windowId);
            
            if (storedSessionId == null) {
                // First time this window is accessing the channel
                windowSessionMap.put(windowId, currentSessionId);
                session.setAttribute("windowId", windowId);
                logger.debug("New window session mapping created: windowId={}, sessionId={}", windowId, currentSessionId);
            } else if (!storedSessionId.equals(currentSessionId)) {
                // Session ID changed but window ID is the same
                logger.debug("Session ID changed for windowId: {} (old: {}, new: {})", 
                    windowId, storedSessionId, currentSessionId);
                // Update the session mapping
                windowSessionMap.put(windowId, currentSessionId);
            }
            
            // Get the current channel
            var currentChannel = channelService.getChannelByChannelId(channelId);
            if (currentChannel == null) {
                logger.error("Channel not found with ID: {}", channelId);
                return "error/no-window-id";
            }
            model.addAttribute("currentChannel", currentChannel);
            model.addAttribute("channel", new Channel());
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
            
            model.addAttribute("messages", channelService.getMessagesForChannel(channelId));
            model.addAttribute("message", new Message());
            model.addAttribute("windowId", windowId);
            
            return "chat-channel";
        } catch (Exception e) {
            logger.error("Error accessing channel {}: {}", channelId, e.getMessage(), e);
            return "error/no-window-id";
        }
    }

    @PostMapping("/channel/{channelId}/send")
    public String sendMessage(@PathVariable Long channelId,
                            @RequestParam String windowId,
                            @Valid @ModelAttribute Message message,
                            BindingResult bindingResult,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Attempting to send message to channel {} from windowId: {}", channelId, windowId);
            
            if (bindingResult.hasErrors()) {
                bindingResult.getAllErrors().forEach(error -> 
                    redirectAttributes.addFlashAttribute("error", error.getDefaultMessage()));
                return "redirect:/channel/" + channelId + "?windowId=" + windowId;
            }

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
            redirectAttributes.addFlashAttribute("error", "Failed to send message: " + e.getMessage());
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
            logger.debug("Channel {} and its messages deleted successfully");
            
            // Notify clients of the channel deletion
            notifyChannelUpdate(channelId, "channel_deleted");
            
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
                            @Valid @ModelAttribute Channel channel,
                            BindingResult bindingResult,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        try {
            logger.debug("Attempting to edit channel {} from windowId: {}", channelId, windowId);
            
            if (bindingResult.hasErrors()) {
                bindingResult.getAllErrors().forEach(error -> 
                    redirectAttributes.addFlashAttribute("error", error.getDefaultMessage()));
                return "redirect:/channel/" + channelId + "?windowId=" + windowId;
            }

            // Verify user is logged in
            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.error("No user logged in for windowId: {}", windowId);
                redirectAttributes.addFlashAttribute("error", "Please sign in to edit channels");
                return "redirect:/welcome?windowId=" + windowId;
            }

            // Get and update the channel
            Channel existingChannel = channelService.getChannelByChannelId(channelId);
            String oldName = existingChannel.getChannelName();
            String newName = channel.getChannelName().trim();
            existingChannel.setChannelName(newName);
            channelService.createChannel(existingChannel);
            
            logger.info("Channel {} renamed from '{}' to '{}' by user {}", 
                channelId, oldName, newName, username);
            
            // Prepare update data
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("channelId", channelId);
            updateData.put("oldName", oldName);
            updateData.put("newName", newName);
            updateData.put("timestamp", System.currentTimeMillis());
            updateData.put("updatedBy", username);
            
            // Broadcast to all channels to update their channel lists
            for (Map.Entry<Long, Set<SseEmitter>> entry : channelEmitters.entrySet()) {
                Long targetChannelId = entry.getKey();
                Set<SseEmitter> emitters = entry.getValue();
                
                if (emitters != null && !emitters.isEmpty()) {
                    List<SseEmitter> deadEmitters = new ArrayList<>();
                    
                    // Add channel list update flag for non-current channel clients
                    Map<String, Object> broadcastData = new HashMap<>(updateData);
                    broadcastData.put("updateChannelList", !targetChannelId.equals(channelId));
                    
                    emitters.forEach(emitter -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("channel_updated")
                                .data(broadcastData));
                            logger.debug("Sent channel update to client in channel {}", targetChannelId);
                        } catch (Exception e) {
                            logger.debug("Failed to send channel update to client in channel {}: {}", 
                                targetChannelId, e.getMessage());
                            deadEmitters.add(emitter);
                        }
                    });
                    
                    // Clean up dead emitters
                    deadEmitters.forEach(emitter -> {
                        emitters.remove(emitter);
                        emitter.complete();
                    });
                    
                    if (emitters.isEmpty()) {
                        channelEmitters.remove(targetChannelId);
                    }
                }
            }
            
            logger.debug("Channel {} update broadcast complete", channelId);
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

    @PostMapping("/channel/{channelId}/typing")
    @ResponseBody
    public void updateTypingStatus(@PathVariable Long channelId,
                                 @RequestParam(required = false) String windowId,
                                 @RequestBody Map<String, Object> payload,
                                 HttpSession session) {
        try {
            if (windowId == null || windowId.trim().isEmpty()) {
                logger.warn("No windowId provided for typing status update");
                return;
            }

            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) return;

            boolean isTyping = (boolean) payload.get("isTyping");
            Set<String> channelTypingUsers = typingUsersByChannel.computeIfAbsent(channelId, k -> new CopyOnWriteArraySet<>());

            if (isTyping) {
                channelTypingUsers.add(username);
            } else {
                channelTypingUsers.remove(username);
            }

            if (channelTypingUsers.isEmpty()) {
                typingUsersByChannel.remove(channelId);
            }
        } catch (Exception e) {
            logger.error("Error updating typing status: {}", e.getMessage(), e);
        }
    }

    @GetMapping("/channel/{channelId}/messages")
    @ResponseBody
    public Map<String, Object> getMessages(@PathVariable Long channelId, 
                                         @RequestParam(required = false) String windowId,
                                         @RequestParam(required = false, defaultValue = "0") Long lastMessageId,
                                         HttpSession session) {
        try {
            if (windowId == null || windowId.trim().isEmpty()) {
                logger.debug("No windowId provided for messages request");
                return Map.of("error", "No window ID provided");
            }

            String currentSessionId = session.getId();
            String storedSessionId = windowSessionMap.get(windowId);
            
            if (storedSessionId != null && !storedSessionId.equals(currentSessionId)) {
                logger.debug("Session ID mismatch for windowId: {} (stored: {}, current: {})", 
                    windowId, storedSessionId, currentSessionId);
                windowSessionMap.put(windowId, currentSessionId);
            }

            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.debug("No username found for windowId: {}", windowId);
                return new HashMap<>();
            }

            User currentUser = userService.getUserByUsername(username);
            if (currentUser == null) {
                logger.error("User not found for username: {}", username);
                session.removeAttribute("username_" + windowId);
                return new HashMap<>();
            }

            // Update user's last activity time
            updateUserActivity(channelId, username);

            List<Message> allMessages = channelService.getMessagesForChannel(channelId);
            List<Message> newMessages = allMessages.stream()
                .filter(message -> message.getMessageId() > lastMessageId)
                .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> messageData = newMessages.stream()
                .map(message -> {
                    Map<String, Object> messageMap = new HashMap<>();
                    messageMap.put("id", message.getMessageId());
                    messageMap.put("content", message.getContent());
                    messageMap.put("timestamp", message.getTimestamp());
                    messageMap.put("username", message.getUser().getUsername());
                    messageMap.put("isCurrentUser", message.getUser().getUsername().equals(currentUser.getUsername()));
                    return messageMap;
                })
                .collect(Collectors.toList());

            response.put("messages", messageData);
            response.put("typingUsers", typingUsersByChannel.getOrDefault(channelId, new CopyOnWriteArraySet<>()));
            response.put("activeUsers", getActiveUsersCount(channelId));
            return response;
        } catch (NotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found", e);
        } catch (Exception e) {
            logger.error("Error getting messages: {}", e.getMessage(), e);
            return Map.of("error", "Failed to get messages: " + e.getMessage());
        }
    }

    @GetMapping(value = "/channel/{channelId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter subscribeToChannelEvents(@PathVariable Long channelId,
                                             @RequestParam(required = false) String windowId,
                                             HttpSession session) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        if (windowId == null || windowId.trim().isEmpty()) {
            emitter.completeWithError(new IllegalStateException("No window ID provided"));
            return emitter;
        }
        
        String username = (String) session.getAttribute("username_" + windowId);
        if (username == null) {
            emitter.completeWithError(new IllegalStateException("Not authenticated"));
            return emitter;
        }

        channelEmitters.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        
        emitter.onCompletion(() -> {
            Set<SseEmitter> emitters = channelEmitters.get(channelId);
            if (emitters != null) {
                emitters.remove(emitter);
                if (emitters.isEmpty()) {
                    channelEmitters.remove(channelId);
                }
            }
        });
        
        emitter.onTimeout(emitter::complete);
        
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("status", "connected", "channelId", channelId)));
        } catch (IOException e) {
            logger.error("Error sending initial SSE event", e);
        }
        
        return emitter;
    }

    @PostMapping(value = "/channel/{channelId}/message", 
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> sendMessageAjax(@PathVariable Long channelId,
                                             @RequestParam(required = false) String windowId,
                                             @RequestBody Map<String, String> payload,
                                             HttpSession session) {
        try {
            if (windowId == null || windowId.trim().isEmpty()) {
                logger.warn("No windowId provided for message request");
                return Map.of("success", false, "error", "No window ID provided");
            }

            logger.debug("Received message request - channelId: {}, windowId: {}, sessionId: {}, payload: {}", 
                channelId, windowId, session.getId(), payload);
            
            if (payload == null || !payload.containsKey("content")) {
                logger.warn("Invalid payload received - channelId: {}, windowId: {}, payload: {}", 
                    channelId, windowId, payload);
                return Map.of("success", false, "error", "Invalid message format");
            }
            
            String content = payload.get("content");
            if (content == null || content.trim().isEmpty()) {
                logger.warn("Empty message content received - channelId: {}, windowId: {}", channelId, windowId);
                return Map.of("success", false, "error", "Message content cannot be empty");
            }

            String username = (String) session.getAttribute("username_" + windowId);
            logger.debug("Retrieved username from session - windowId: {}, username: {}", windowId, username);
            
            if (username == null) {
                logger.warn("No user session found - windowId: {}, sessionId: {}", windowId, session.getId());
                return Map.of("success", false, "error", "Please sign in to send messages");
            }

            User currentUser = userService.getUserByUsername(username);
            logger.debug("Retrieved user from database - username: {}, found: {}", username, currentUser != null);
            
            if (currentUser == null) {
                logger.error("User not found in database - username: {}", username);
                session.removeAttribute("username_" + windowId);
                return Map.of("success", false, "error", "User session expired");
            }

            Channel channel = channelService.getChannelByChannelId(channelId);
            logger.debug("Retrieved channel from database - channelId: {}, found: {}", channelId, channel != null);
            
            if (channel == null) {
                logger.error("Channel not found in database - channelId: {}", channelId);
                return Map.of("success", false, "error", "Channel not found");
            }

            Message message = new Message();
            message.setContent(content.trim());
            message.setUser(currentUser);
            message.setChannel(channel);
            
            logger.debug("Attempting to save message - channelId: {}, username: {}, content length: {}", 
                channelId, username, content.length());
            
            try {
                channelService.sendMessage(channelId, message);
                logger.debug("Message saved successfully - messageId: {}", message.getMessageId());
            } catch (Exception e) {
                logger.error("Error saving message - channelId: {}, username: {}, error: {}", 
                    channelId, username, e.getMessage(), e);
                throw e;
            }
            
            logger.debug("Notifying clients of new message - channelId: {}", channelId);
            notifyChannelUpdate(channelId, "new_message");
            
            return Map.of("success", true, "message", "Message sent successfully");
        } catch (Exception e) {
            logger.error("Error sending message: {}", e.getMessage(), e);
            return Map.of("success", false, "error", "Failed to send message: " + e.getMessage());
        }
    }

    private void notifyChannelUpdate(Long channelId, String eventType) {
        Set<SseEmitter> emitters = channelEmitters.get(channelId);
        if (emitters != null) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            
            emitters.forEach(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(Map.of("timestamp", System.currentTimeMillis())));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            });
            
            deadEmitters.forEach(emitter -> {
                emitters.remove(emitter);
                emitter.complete();
            });
            
            if (emitters.isEmpty()) {
                channelEmitters.remove(channelId);
            }
        }
    }

    private void updateUserActivity(Long channelId, String username) {
        Map<String, Long> channelUsers = activeUsersByChannel.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
        channelUsers.put(username, System.currentTimeMillis());
        
        // Clean up inactive users
        long currentTime = System.currentTimeMillis();
        channelUsers.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > USER_ACTIVITY_TIMEOUT);
        
        if (channelUsers.isEmpty()) {
            activeUsersByChannel.remove(channelId);
        }
    }

    private int getActiveUsersCount(Long channelId) {
        Map<String, Long> channelUsers = activeUsersByChannel.get(channelId);
        if (channelUsers == null) return 0;
        
        // Clean up inactive users while counting
        long currentTime = System.currentTimeMillis();
        channelUsers.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > USER_ACTIVITY_TIMEOUT);
        
        return channelUsers.size();
    }

    // Add this new mapping to catch invalid channel URLs
    @GetMapping("/channel/**")
    public String handleInvalidChannelUrl() {
        logger.debug("Invalid channel URL accessed, redirecting to error page");
        return "error/no-window-id";
    }

    @GetMapping(value = "/channels", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> getChannelsList(HttpSession session,
                                                   @RequestParam(required = false) String windowId) {
        try {
            logger.debug("Getting channels list for windowId: {}", windowId);
            
            if (windowId == null || windowId.trim().isEmpty()) {
                logger.warn("No windowId provided for channels request");
                return Collections.emptyList();
            }

            String username = (String) session.getAttribute("username_" + windowId);
            if (username == null) {
                logger.warn("No user session found for windowId: {}", windowId);
                return Collections.emptyList();
            }

            List<Channel> channels = channelService.getAllChannels();
            return channels.stream()
                .map(channel -> {
                    Map<String, Object> channelData = new HashMap<>();
                    channelData.put("channelId", channel.getChannelId());
                    channelData.put("channelName", channel.getChannelName());
                    channelData.put("activeUsers", getActiveUsersCount(channel.getChannelId()));
                    return channelData;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error getting channels list: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
