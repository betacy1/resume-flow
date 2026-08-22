package com.resumeflow.repository;

import com.resumeflow.entity.AwardCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AwardCertificateRepository extends JpaRepository<AwardCertificate, Long> {

    List<AwardCertificate> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
