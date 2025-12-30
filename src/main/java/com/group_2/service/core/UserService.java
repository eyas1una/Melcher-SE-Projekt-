package com.group_2.service.core;

import com.group_2.model.User;
import com.group_2.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String name) {
        User user = new User(name);
        return userRepository.save(user);
    }

    @Transactional
    public User registerUser(String name, String surname, String email, String password) {
        // Check if email exists
        if (userRepository.findAll().stream().anyMatch(u -> u.getEmail() != null && u.getEmail().equals(email))) {
            throw new RuntimeException("Email already exists");
        }
        User user = new User(name, surname, email, password);
        return userRepository.save(user);
    }

    public Optional<User> authenticate(String email, String password) {
        return userRepository.findAll().stream().filter(u -> u.getEmail() != null && u.getEmail().equals(email)
                && u.getPassword() != null && u.getPassword().equals(password)).findFirst();
    }

    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUser(Long id, String name, String surname, String email) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        user.setName(name);
        user.setSurname(surname);
        user.setEmail(email);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Returns a formatted display name for a user (e.g., "John D."). Returns
     * "Unknown" if user not found.
     */
    public String getDisplayName(Long userId) {
        return userRepository.findById(userId).map(this::formatDisplayName).orElse("Unknown");
    }

    /**
     * Returns a map of userId -> display name for multiple users. Missing users are
     * mapped to "Unknown".
     */
    public Map<Long, String> getDisplayNames(List<Long> userIds) {
        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return userIds.stream().collect(Collectors.toMap(id -> id,
                id -> userMap.containsKey(id) ? formatDisplayName(userMap.get(id)) : "Unknown"));
    }

    private String formatDisplayName(User user) {
        String name = user.getName();
        if (user.getSurname() != null && !user.getSurname().isEmpty()) {
            name += " " + user.getSurname().charAt(0) + ".";
        }
        return name;
    }
}
