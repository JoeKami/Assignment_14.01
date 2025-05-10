package com.coderscampus.PollyChat.assignment142.service;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.Message;
import com.coderscampus.PollyChat.assignment142.repository.MessageRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    public List<Message> getMessagesForChannel(Channel channel) {
        return messageRepository.findByChannelOrderByMessageIdAsc(channel);
    }
}
