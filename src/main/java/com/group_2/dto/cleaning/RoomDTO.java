package com.group_2.dto.cleaning;

/**
 * Immutable DTO for rooms to decouple UI from JPA entities.
 */
public record RoomDTO(Long id, String name) {
}
