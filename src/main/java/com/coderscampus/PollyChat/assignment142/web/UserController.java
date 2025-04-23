package com.coderscampus.PollyChat.assignment142.web;

import com.coderscampus.PollyChat.assignment142.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserController extends JpaRepository<User, Long> {
}
