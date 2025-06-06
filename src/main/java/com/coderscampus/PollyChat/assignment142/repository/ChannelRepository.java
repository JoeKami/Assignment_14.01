package com.coderscampus.PollyChat.assignment142.repository;

import com.coderscampus.PollyChat.assignment142.domain.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

}
