package com.coderscampus.PollyChat.assignment142.service;

import com.coderscampus.PollyChat.assignment142.domain.User;
import com.coderscampus.PollyChat.assignment142.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(User newUser) {
        // First, try to find any existing user with this username
        Query query = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.username = :username ORDER BY u.userId ASC");
        query.setParameter("username", newUser.getUsername());
        
        @SuppressWarnings("unchecked")
        List<User> existingUsers = query.getResultList();
        
        if (!existingUsers.isEmpty()) {
            // If we found existing users, use the first one (oldest by ID)
            User existingUser = existingUsers.get(0);
            
            // If there are duplicates, delete them
            if (existingUsers.size() > 1) {
                for (int i = 1; i < existingUsers.size(); i++) {
                    entityManager.remove(existingUsers.get(i));
                }
            }
            
            return existingUser;
        } else {
            // No existing user found, create new one
            return userRepository.save(newUser);
        }
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User getUserByUsername(String username) {
        Query query = entityManager.createQuery(
            "SELECT u FROM User u WHERE u.username = :username ORDER BY u.userId ASC");
        query.setParameter("username", username);
        query.setMaxResults(1); // Only get the first result
        
        @SuppressWarnings("unchecked")
        List<User> users = query.getResultList();
        return users.isEmpty() ? null : users.get(0);
    }
}
