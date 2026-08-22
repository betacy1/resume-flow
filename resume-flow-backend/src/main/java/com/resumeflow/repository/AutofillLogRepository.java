package com.resumeflow.repository;

import com.resumeflow.entity.AutofillLog;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AutofillLogRepository extends JpaRepository<AutofillLog, Long> {

    Page<AutofillLog> findByUserIdAndDeletedFalseOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
