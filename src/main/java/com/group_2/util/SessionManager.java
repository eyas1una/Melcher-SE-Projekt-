package com.group_2.util;

import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.service.core.UserService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring-managed session manager that maintains only the session snapshot (IDs and
 * basic profile data) instead of holding onto JPA entities. Controllers can still
 * fetch a fresh User on demand via the provided helpers, but the authoritative
 * state is the lightweight snapshot.
 */
@Component
public class SessionManager {

    private final UserService userService;

    private Long currentUserId;
    private Long currentWgId;
    private String currentUserName;
    private String currentUserSurname;

    public SessionManager(UserService userService) {
        this.userService = userService;
    }

    /**
     * Sets the currently logged-in user by snapshotting only the necessary data.
     */
    public void setCurrentUser(User user) {
        if (user == null) {
            clear();
            return;
        }
        this.currentUserId = user.getId();
        this.currentWgId = user.getWg() != null ? user.getWg().getId() : null;
        this.currentUserName = user.getName();
        this.currentUserSurname = user.getSurname();
    }

    /**
     * Returns the current user snapshot (id, name, surname, wgId) if logged in.
     */
    public Optional<UserSession> getCurrentUserSession() {
        if (currentUserId == null) {
            return Optional.empty();
        }
        return Optional.of(new UserSession(currentUserId, currentUserName, currentUserSurname, currentWgId));
    }

    /**
     * Retrieves the currently logged-in user entity (fresh from the database).
     * Kept for backward compatibility while gradually removing direct entity use
     * from controllers.
     */
    public User getCurrentUser() {
        if (currentUserId == null) {
            return null;
        }
        return userService.getUser(currentUserId).orElse(null);
    }

    /**
     * Retrieves the user ID of the currently logged-in user.
     */
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Retrieves the WG ID of the currently logged-in user if known.
     */
    public Long getCurrentWgId() {
        return currentWgId;
    }

    /**
     * Retrieves the WG of the currently logged-in user (fresh entity).
     */
    public WG getCurrentUserWG() {
        User user = getCurrentUser();
        return user != null ? user.getWg() : null;
    }

    /**
     * Checks if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return currentUserId != null;
    }

    /**
     * Clears the session snapshot.
     */
    public void clear() {
        currentUserId = null;
        currentWgId = null;
        currentUserName = null;
        currentUserSurname = null;
    }

    /**
     * Refreshes the current user data from the database and updates the snapshot.
     */
    public void refreshCurrentUser() {
        if (currentUserId == null) {
            return;
        }
        userService.getUser(currentUserId).ifPresentOrElse(this::setCurrentUser, this::clear);
    }

    /**
     * Snapshot of minimal user information for UI consumption.
     */
    public record UserSession(Long userId, String name, String surname, Long wgId) {
        public String displayName() {
            if (surname == null || surname.isBlank()) {
                return name;
            }
            return name + " " + surname;
        }
    }
}
