package com.resumeflow.repository;

import com.resumeflow.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {

    List<FamilyMember> findByUserIdAndDeletedFalseOrderBySortOrderAscIdAsc(Long userId);

    long countByUserIdAndDeletedFalse(Long userId);

    void deleteByUserId(Long userId);
}
