package com.resumeflow.repository;

import com.resumeflow.entity.ContentVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentVariantRepository extends JpaRepository<ContentVariant, Long> {

    List<ContentVariant> findByUserIdAndDeletedFalseOrderBySourceTypeAscSourceIdAscAudienceTypeAscLengthTypeAsc(Long userId);

    List<ContentVariant> findByUserIdAndSourceTypeAndSourceIdAndDeletedFalseOrderByAudienceTypeAscLengthTypeAsc(
            Long userId, String sourceType, Long sourceId);

    Optional<ContentVariant> findByUserIdAndSourceTypeAndSourceIdAndAudienceTypeAndLengthTypeAndEnabledTrueAndDeletedFalse(
            Long userId, String sourceType, Long sourceId, String audienceType, String lengthType);

    List<ContentVariant> findByUserIdAndSourceTypeAndSourceIdAndAudienceTypeAndEnabledTrueAndDeletedFalse(
            Long userId, String sourceType, Long sourceId, String audienceType);

    long countByUserIdAndDeletedFalse(Long userId);

    long countByUserIdAndJobDirectionNotNullAndDeletedFalse(Long userId);

    long countByUserIdAndSourceTypeAndDeletedFalse(Long userId, String sourceType);

    List<ContentVariant> findByUserIdAndSourceTypeAndDeletedFalse(Long userId, String sourceType);

    void deleteByUserId(Long userId);
}
