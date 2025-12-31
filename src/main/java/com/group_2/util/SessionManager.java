package com.group_2.util;

import com.group_2.dto.core.UserSessionDTO;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.service.core.UserService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring-managed session manager that maintains only the session snapshot (IDs and
 * basic profile data) instead of holding onto JPA entities. Controllers consume
 * the lightweight snapshot and re-fetch through services when needed.
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
     * Sets the currently logged-in user by summary data (no entity reference).
     */
    public void setCurrentUserSummary(UserSummaryDTO user) {
        if (user == null) {
            clear();
            return;
        }
        this.currentUserId = user.id();
        this.currentWgId = user.wgId();
        this.currentUserName = user.name();
        this.currentUserSurname = user.surname();
    }

    /**
     * Returns the current user snapshot (id, name, surname, wgId) if logged in.
     */
    public Optional<UserSessionDTO> getCurrentUserSession() {
        if (currentUserId == null) {
            return Optional.empty();
        }
        return Optional.of(new UserSessionDTO(currentUserId, currentUserName, currentUserSurname, currentWgId));
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
        userService.getUserSummary(currentUserId).ifPresentOrElse(this::setCurrentUserSummary, this::clear);
    }
}
