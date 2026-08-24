package com.resumeflow.repository;

import com.resumeflow.entity.TemplateExperienceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateExperienceConfigRepository extends JpaRepository<TemplateExperienceConfig, Long> {

    List<TemplateExperienceConfig> findByUserIdAndTemplateIdAndDeletedFalse(Long userId, Long templateId);

    List<TemplateExperienceConfig> findByUserIdAndDeletedFalse(Long userId);

    Optional<TemplateExperienceConfig> findByUserIdAndTemplateIdAndSourceTypeAndSourceIdAndDeletedFalse(
            Long userId, Long templateId, String sourceType, Long sourceId);

    long countByUserIdAndDeletedFalse(Long userId);

    void deleteByUserId(Long userId);
}
