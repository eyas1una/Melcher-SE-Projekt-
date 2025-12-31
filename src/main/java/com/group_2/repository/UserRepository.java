package com.group_2.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.group_2.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    java.util.List<User> findByWgId(Long wgId);

    long countByWgId(Long wgId);

    boolean existsByIdAndWgId(Long id, Long wgId);
}
