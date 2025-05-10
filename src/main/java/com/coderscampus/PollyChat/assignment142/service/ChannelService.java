package com.coderscampus.PollyChat.assignment142.service;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.Message;
import com.coderscampus.PollyChat.assignment142.repository.ChannelRepository;
import com.coderscampus.PollyChat.assignment142.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;

    @Autowired
    public ChannelService(ChannelRepository channelRepository, MessageRepository messageRepository) {
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
    }

    public List<Channel> getAllChannels() {
        return channelRepository.findAll();
    }

    public Channel getChannelByChannelId(Long channelId) throws ChangeSetPersister.NotFoundException {
        return channelRepository.findById(channelId).orElseThrow(ChangeSetPersister.NotFoundException::new);
    }

    public Channel createChannel(Channel channel) {
        return channelRepository.save(channel);
    }

    public void deleteChannel(Long channelId) {
        channelRepository.deleteById(channelId);
    }

    public List<Message> getMessagesForChannel(Long channelId) throws ChangeSetPersister.NotFoundException {
        Channel channel = getChannelByChannelId(channelId);
        return messageRepository.findByChannelOrderByMessageIdAsc(channel);
    }

    public void sendMessage(Long channelId, Message message) throws ChangeSetPersister.NotFoundException {
        Channel channel = getChannelByChannelId(channelId);
        message.setChannel(channel);
        messageRepository.save(message);
    }
}
