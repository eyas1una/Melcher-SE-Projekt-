package com.group_2.service;

import com.group_2.repository.RoomRepository;
import com.group_2.repository.UserRepository;
import com.group_2.repository.WGRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseCleanupService {

    private final WGRepository wgRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    @Autowired
    public DatabaseCleanupService(WGRepository wgRepository, UserRepository userRepository,
            RoomRepository roomRepository) {
        this.wgRepository = wgRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public void cleanupDatabase() {
        wgRepository.deleteAll();
        userRepository.deleteAll();
        roomRepository.deleteAll();
    }
}
