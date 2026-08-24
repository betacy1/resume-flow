package com.resumeflow.repository;

import com.resumeflow.entity.ApplicationStageRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 投递流程记录仓库
 */
public interface ApplicationStageRecordRepository extends JpaRepository<ApplicationStageRecord, Long> {

    List<ApplicationStageRecord> findByApplicationRecordIdAndUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(
            Long applicationRecordId, Long userId);

    long countByApplicationRecordIdAndUserIdAndDeletedFalse(Long applicationRecordId, Long userId);
}
