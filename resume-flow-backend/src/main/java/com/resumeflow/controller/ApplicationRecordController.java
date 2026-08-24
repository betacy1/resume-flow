package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.*;
import com.resumeflow.service.ApplicationExcelService;
import com.resumeflow.service.ApplicationRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 投递信息表 / 秋招投递记录（Application Tracker）
 */
@Tag(name = "投递信息表接口")
@RestController
@RequiredArgsConstructor
public class ApplicationRecordController {

    private final ApplicationRecordService recordService;
    private final ApplicationExcelService excelService;

    @Operation(summary = "查询投递记录（分页、筛选、搜索、排序）")
    @GetMapping("/api/application-records")
    public Result<Map<String, Object>> list(ApplicationRecordService.QueryParams query) {
        return Result.success(recordService.list(query));
    }

    @Operation(summary = "下拉选项：状态、批次、企业性质、投递渠道、阶段等")
    @GetMapping("/api/application-records/options")
    public Result<Map<String, Object>> options() {
        return Result.success(recordService.options());
    }

    @Operation(summary = "导出投递信息表 Excel")
    @GetMapping("/api/application-records/export")
    public void export(HttpServletResponse response) {
        excelService.export(response);
    }

    @Operation(summary = "导入投递信息表 Excel")
    @PostMapping("/api/application-records/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(excelService.importExcel(file));
    }

    @Operation(summary = "插件自动采集并写入/更新投递记录")
    @PostMapping("/api/application-records/capture")
    public Result<ApplicationCaptureResult> capture(@RequestBody ApplicationCaptureRequest request) {
        return Result.success(recordService.capture(request));
    }

    @Operation(summary = "手动新增投递记录")
    @PostMapping("/api/application-records")
    public Result<ApplicationRecordDTO> create(@RequestBody ApplicationRecordDTO dto) {
        return Result.success(recordService.create(dto));
    }

    @Operation(summary = "批量修改状态")
    @PostMapping("/api/application-records/batch-status")
    public Result<Integer> batchStatus(@RequestBody BatchStatusRequest request) {
        return Result.success(recordService.batchUpdateStatus(request.getIds(), request.getApplyStatus()));
    }

    @Operation(summary = "编辑投递记录")
    @PutMapping("/api/application-records/{id}")
    public Result<ApplicationRecordDTO> update(@PathVariable Long id, @RequestBody ApplicationRecordDTO dto) {
        return Result.success(recordService.update(id, dto));
    }

    @Operation(summary = "快速修改状态")
    @PutMapping("/api/application-records/{id}/status")
    public Result<ApplicationRecordDTO> updateStatus(@PathVariable Long id, @RequestParam String applyStatus) {
        return Result.success(recordService.updateStatus(id, applyStatus));
    }

    @Operation(summary = "逻辑删除投递记录")
    @DeleteMapping("/api/application-records/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return Result.success();
    }

    @Operation(summary = "复制投递记录")
    @PostMapping("/api/application-records/{id}/copy")
    public Result<ApplicationRecordDTO> copy(@PathVariable Long id) {
        return Result.success(recordService.copy(id));
    }

    @Operation(summary = "查询流程记录")
    @GetMapping("/api/application-records/{id}/stages")
    public Result<List<ApplicationStageRecordDTO>> listStages(@PathVariable Long id) {
        return Result.success(recordService.listStages(id));
    }

    @Operation(summary = "新增流程记录")
    @PostMapping("/api/application-records/{id}/stages")
    public Result<ApplicationStageRecordDTO> createStage(@PathVariable Long id,
                                                         @RequestBody ApplicationStageRecordDTO dto) {
        return Result.success(recordService.createStage(id, dto));
    }

    @Operation(summary = "编辑流程记录")
    @PutMapping("/api/application-stages/{stageId}")
    public Result<ApplicationStageRecordDTO> updateStage(@PathVariable Long stageId,
                                                         @RequestBody ApplicationStageRecordDTO dto) {
        return Result.success(recordService.updateStage(stageId, dto));
    }

    @Operation(summary = "删除流程记录")
    @DeleteMapping("/api/application-stages/{stageId}")
    public Result<Void> deleteStage(@PathVariable Long stageId) {
        recordService.deleteStage(stageId);
        return Result.success();
    }

    @Data
    public static class BatchStatusRequest {
        private List<Long> ids;
        private String applyStatus;
    }
}
