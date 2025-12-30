package com.group_2.repository.cleaning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.cleaning.Room;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
}
