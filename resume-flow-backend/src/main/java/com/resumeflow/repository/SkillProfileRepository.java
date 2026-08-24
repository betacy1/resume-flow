package com.resumeflow.repository;

import com.resumeflow.entity.SkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {

    List<SkillProfile> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    Optional<SkillProfile> findByUserIdAndSkillKeyAndDeletedFalse(Long userId, String skillKey);

    void deleteByUserId(Long userId);
}
