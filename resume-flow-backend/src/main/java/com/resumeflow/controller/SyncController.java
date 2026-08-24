package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.service.SyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 插件数据同步接口
 */
@Tag(name = "数据同步接口")
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @Operation(summary = "同步状态：数据版本号、内容哈希、最后更新时间")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(syncService.status());
    }

    @Operation(summary = "全量同步：拉取当前用户完整可填写数据（全部字段，无脱敏）")
    @GetMapping("/full")
    public Result<Map<String, Object>> full() {
        return Result.success(syncService.fullPayload());
    }
}
