package com.group_2.dto.core;

import com.group_2.dto.cleaning.RoomDTO;

import java.util.List;

/**
 * View-model for WG settings screens. Keeps UI layers off entities.
 */
public record WgDetailsViewDTO(Long id, String name, String inviteCode, UserSummaryDTO admin,
        List<UserSummaryDTO> members, List<RoomDTO> rooms) {
    public int memberCount() {
        return members != null ? members.size() : 0;
    }

    public int roomCount() {
        return rooms != null ? rooms.size() : 0;
    }
}
