package com.group_2.repository;

import com.group_2.WG;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WGRepository extends JpaRepository<WG, Long> {
    java.util.Optional<WG> findByInviteCode(String inviteCode);
}
