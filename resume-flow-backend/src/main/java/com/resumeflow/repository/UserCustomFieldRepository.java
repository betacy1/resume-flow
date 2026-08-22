package com.resumeflow.repository;

import com.resumeflow.entity.UserCustomField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCustomFieldRepository extends JpaRepository<UserCustomField, Long> {

    @Query("""
            SELECT f FROM UserCustomField f
            WHERE f.userId = :userId
              AND f.deleted = false
              AND (:category IS NULL OR f.fieldCategory = :category)
              AND (:enabled IS NULL OR f.enabled = :enabled)
              AND (:templateId IS NULL OR f.templateId = :templateId OR f.templateId IS NULL)
              AND (:keyword IS NULL OR LOWER(f.fieldName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(f.fieldKey) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY f.sortOrder ASC, f.id ASC
            """)
    List<UserCustomField> findByConditions(@Param("userId") Long userId,
                                           @Param("category") String category,
                                           @Param("enabled") Boolean enabled,
                                           @Param("templateId") Long templateId,
                                           @Param("keyword") String keyword);

    List<UserCustomField> findByUserIdAndEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
