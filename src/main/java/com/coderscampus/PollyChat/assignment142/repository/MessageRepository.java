package com.coderscampus.PollyChat.assignment142.repository;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import com.coderscampus.PollyChat.assignment142.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChannelOrderByMessageIdAsc(Channel channel);
}
