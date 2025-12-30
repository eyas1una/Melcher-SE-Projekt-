package com.group_2.dto.shopping;

/**
 * Immutable DTO for shopping list items to decouple UI from JPA entities.
 */
public record ShoppingListItemDTO(Long id, String name, Long creatorId, String creatorName, boolean bought) {
}
