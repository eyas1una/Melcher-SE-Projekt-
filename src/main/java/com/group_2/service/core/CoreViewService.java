package com.group_2.service.core;

import com.group_2.dto.cleaning.CleaningMapper;
import com.group_2.dto.cleaning.RoomDTO;
import com.group_2.dto.core.CoreMapper;
import com.group_2.dto.core.UserProfileViewDTO;
import com.group_2.dto.core.UserSummaryDTO;
import com.group_2.dto.core.WgDetailsViewDTO;
import com.group_2.dto.core.WgSummaryDTO;
import com.group_2.model.User;
import com.group_2.model.WG;
import com.group_2.repository.UserRepository;
import com.group_2.repository.WGRepository;
import com.group_2.repository.cleaning.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * View-facing facade for core domain data. Provides DTOs for UI controllers.
 */
@Service
public class CoreViewService {

    private final UserRepository userRepository;
    private final WGRepository wgRepository;
    private final RoomRepository roomRepository;
    private final CoreMapper coreMapper;
    private final CleaningMapper cleaningMapper;

    @Autowired
    public CoreViewService(UserRepository userRepository, WGRepository wgRepository, RoomRepository roomRepository,
            CoreMapper coreMapper, CleaningMapper cleaningMapper) {
        this.userRepository = userRepository;
        this.wgRepository = wgRepository;
        this.roomRepository = roomRepository;
        this.coreMapper = coreMapper;
        this.cleaningMapper = cleaningMapper;
    }

    public UserProfileViewDTO getUserProfile(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        UserSummaryDTO summary = coreMapper.toUserSummary(user);
        WG wg = user.getWg();
        WgSummaryDTO wgSummary = coreMapper.toWgSummary(wg);
        boolean isAdmin = wg != null && wg.getAdmin() != null && wg.getAdmin().getId() != null
                && wg.getAdmin().getId().equals(userId);
        return new UserProfileViewDTO(summary, wgSummary, isAdmin);
    }

    public WgDetailsViewDTO getWgDetails(Long wgId) {
        if (wgId == null) {
            return null;
        }
        WG wg = wgRepository.findById(wgId).orElse(null);
        if (wg == null) {
            return null;
        }
        List<UserSummaryDTO> members = coreMapper.toUserSummaries(userRepository.findByWgId(wg.getId()));
        List<RoomDTO> rooms = cleaningMapper.toRoomDTOList(roomRepository.findByWgId(wg.getId()));
        return new WgDetailsViewDTO(wg.getId(), wg.getName(), wg.getInviteCode(), coreMapper.toUserSummary(wg.getAdmin()),
                members, rooms);
    }

    public WgSummaryDTO getWgSummary(Long wgId) {
        if (wgId == null) {
            return null;
        }
        WG wg = wgRepository.findById(wgId).orElse(null);
        return coreMapper.toWgSummary(wg);
    }
}
