package com.resumeflow.repository;

import com.resumeflow.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserIdAndDeletedFalse(Long userId);
}
