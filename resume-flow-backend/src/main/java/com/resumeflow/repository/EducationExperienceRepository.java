package com.resumeflow.repository;

import com.resumeflow.entity.EducationExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationExperienceRepository extends JpaRepository<EducationExperience, Long> {

    List<EducationExperience> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
