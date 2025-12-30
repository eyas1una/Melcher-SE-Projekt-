package com.group_2.dto.shopping;

import java.util.List;

/**
 * Immutable DTO for shopping lists to decouple UI from JPA entities.
 */
public record ShoppingListDTO(Long id, String name, Long creatorId, String creatorName, boolean shared,
        List<Long> sharedWithIds, List<ShoppingListItemDTO> items, int itemCount, int pendingCount, int boughtCount) {

    /**
     * Check if a given user ID is the creator of this list.
     */
    public boolean isCreator(Long userId) {
        return creatorId != null && creatorId.equals(userId);
    }

    /**
     * Check if a given user ID has access to this list.
     */
    public boolean hasAccess(Long userId) {
        if (userId == null)
            return false;
        if (isCreator(userId))
            return true;
        return sharedWithIds != null && sharedWithIds.contains(userId);
    }
}
