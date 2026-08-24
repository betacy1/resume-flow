package com.resumeflow.repository;

import com.resumeflow.entity.ProfileSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfileSyncStateRepository extends JpaRepository<ProfileSyncState, Long> {

    Optional<ProfileSyncState> findByUserIdAndDeletedFalse(Long userId);

    void deleteByUserId(Long userId);
}
