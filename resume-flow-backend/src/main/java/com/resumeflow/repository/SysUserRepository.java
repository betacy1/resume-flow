package com.resumeflow.repository;

import com.resumeflow.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsernameAndDeletedFalse(String username);

    boolean existsByUsernameAndDeletedFalse(String username);
}
