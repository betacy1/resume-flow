package com.resumeflow.repository;

import com.resumeflow.entity.InternshipExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternshipExperienceRepository extends JpaRepository<InternshipExperience, Long> {

    List<InternshipExperience> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
