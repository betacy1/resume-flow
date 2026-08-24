package com.resumeflow.repository;

import com.resumeflow.entity.ApplicationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 投递信息表仓库
 */
public interface ApplicationRecordRepository
        extends JpaRepository<ApplicationRecord, Long>, JpaSpecificationExecutor<ApplicationRecord> {

    /** 统计用户名下全部记录（含逻辑删除），用于判断是否需要初始化 */
    long countByUserId(Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    List<ApplicationRecord> findByUserIdAndDeletedFalse(Long userId);

    List<ApplicationRecord> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    @Query("select distinct a.batchName from ApplicationRecord a "
            + "where a.userId = :userId and a.deleted = false and a.batchName is not null and a.batchName <> ''")
    List<String> findDistinctBatchNames(@Param("userId") Long userId);

    @Query("select distinct a.companyNature from ApplicationRecord a "
            + "where a.userId = :userId and a.deleted = false and a.companyNature is not null and a.companyNature <> ''")
    List<String> findDistinctCompanyNatures(@Param("userId") Long userId);

    @Query("select distinct a.applicationChannel from ApplicationRecord a "
            + "where a.userId = :userId and a.deleted = false and a.applicationChannel is not null and a.applicationChannel <> ''")
    List<String> findDistinctChannels(@Param("userId") Long userId);

    @Query("select distinct a.currentStage from ApplicationRecord a "
            + "where a.userId = :userId and a.deleted = false and a.currentStage is not null and a.currentStage <> ''")
    List<String> findDistinctCurrentStages(@Param("userId") Long userId);

    @Query("select distinct a.categoryType from ApplicationRecord a "
            + "where a.userId = :userId and a.deleted = false and a.categoryType is not null and a.categoryType <> ''")
    List<String> findDistinctCategoryTypes(@Param("userId") Long userId);

    @Query("select max(a.sortOrder) from ApplicationRecord a where a.userId = :userId and a.deleted = false")
    Integer findMaxSortOrder(@Param("userId") Long userId);
}
