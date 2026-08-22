package com.resumeflow.repository;

import com.resumeflow.entity.ProjectExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectExperienceRepository extends JpaRepository<ProjectExperience, Long> {

    List<ProjectExperience> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
