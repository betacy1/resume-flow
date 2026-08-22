package com.resumeflow.repository;

import com.resumeflow.entity.ApplicationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationTemplateRepository extends JpaRepository<ApplicationTemplate, Long> {

    List<ApplicationTemplate> findByUserIdAndDeletedFalseOrderByIsDefaultDescIdAsc(Long userId);

    java.util.Optional<ApplicationTemplate> findFirstByUserIdAndAudienceTypeAndDeletedFalse(Long userId, String audienceType);

    void deleteByUserId(Long userId);
}
