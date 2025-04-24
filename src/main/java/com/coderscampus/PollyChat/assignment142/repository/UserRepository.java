package com.coderscampus.PollyChat.assignment142.repository;

import com.coderscampus.PollyChat.assignment142.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    User findByUsername(String username);

}
