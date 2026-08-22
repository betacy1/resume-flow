package com.resumeflow.repository;

import com.resumeflow.entity.AnswerMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnswerMaterialRepository extends JpaRepository<AnswerMaterial, Long> {

    List<AnswerMaterial> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    List<AnswerMaterial> findByUserIdAndMaterialTypeAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId, String materialType);

    @Query("""
            SELECT m FROM AnswerMaterial m
            WHERE m.userId = :userId
              AND m.deleted = false
              AND (:materialType IS NULL OR m.materialType = :materialType)
              AND (:templateId IS NULL OR m.templateId = :templateId)
            ORDER BY m.sortOrder ASC, m.id ASC
            """)
    List<AnswerMaterial> findByConditions(
            @Param("userId") Long userId,
            @Param("materialType") String materialType,
            @Param("templateId") Long templateId);

    List<AnswerMaterial> findByUserIdAndEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
