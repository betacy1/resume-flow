package com.resumeflow.init;

import com.resumeflow.entity.SysUser;
import com.resumeflow.repository.SysUserRepository;
import com.resumeflow.service.ApplicationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 投递信息表启动初始化器：为已存在的用户补充初始化投递记录（幂等，已有记录则跳过）。
 * 新用户首次打开投递信息表时也会由服务层懒初始化。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ApplicationDataInitializer implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final ApplicationRecordService recordService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(String... args) {
        try {
            List<SysUser> users = sysUserRepository.findAll();
            for (SysUser user : users) {
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        recordService.ensureInitialized(user.getId());
                    } catch (Exception e) {
                        log.warn("用户 {} 投递信息表初始化失败：{}", user.getId(), e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("投递信息表启动初始化失败：{}", e.getMessage());
        }
    }
}
