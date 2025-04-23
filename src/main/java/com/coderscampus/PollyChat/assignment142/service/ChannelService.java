package com.coderscampus.PollyChat.assignment142.service;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.repository.ChannelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;

    @Autowired
    public ChannelService(ChannelRepository channelRepository) {
    this.channelRepository = channelRepository;
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


}
