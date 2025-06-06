package com.coderscampus.PollyChat.assignment142.repository;

import com.coderscampus.PollyChat.assignment142.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

}
