package com.resumeflow.repository;

import com.resumeflow.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, Long> {

    List<EmergencyContact> findByUserIdAndDeletedFalseOrderByIdAsc(Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    void deleteByUserId(Long userId);
}
