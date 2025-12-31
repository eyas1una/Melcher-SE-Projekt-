package com.group_2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.WG;

@Repository
public interface WGRepository extends JpaRepository<WG, Long> {
    java.util.Optional<WG> findByInviteCode(String inviteCode);

    /**
     * Check if an invite code already exists in the database.
     */
    boolean existsByInviteCode(String inviteCode);
}
