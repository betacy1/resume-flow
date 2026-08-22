package com.resumeflow.repository;

import com.resumeflow.entity.SkillProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillProfileRepository extends JpaRepository<SkillProfile, Long> {

    List<SkillProfile> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
