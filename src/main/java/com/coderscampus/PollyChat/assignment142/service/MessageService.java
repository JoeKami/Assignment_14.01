package com.coderscampus.PollyChat.assignment142.service;

import com.coderscampus.PollyChat.assignment142.repository.MessageRepository;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }
}
