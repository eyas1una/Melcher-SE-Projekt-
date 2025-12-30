package com.group_2.dto.core;

import com.group_2.model.User;
import com.group_2.model.WG;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for core user/WG view models to keep UI layers decoupled from entities.
 */
@Component
public class CoreMapper {

    public UserSummaryDTO toUserSummary(User user) {
        if (user == null) {
            return null;
        }
        Long wgId = user.getWg() != null ? user.getWg().getId() : null;
        return new UserSummaryDTO(user.getId(), user.getName(), user.getSurname(), user.getEmail(), wgId);
    }

    public List<UserSummaryDTO> toUserSummaries(List<User> users) {
        List<UserSummaryDTO> summaries = new ArrayList<>();
        if (users == null) {
            return summaries;
        }
        for (User user : users) {
            summaries.add(toUserSummary(user));
        }
        return summaries;
    }

    public WgSummaryDTO toWgSummary(WG wg) {
        if (wg == null) {
            return null;
        }
        int memberCount = wg.mitbewohner != null ? wg.mitbewohner.size() : 0;
        return new WgSummaryDTO(wg.getId(), wg.name, memberCount);
    }
}
