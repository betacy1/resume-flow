package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.ApplicationTemplateDTO;
import com.resumeflow.service.ApplicationTemplateService;
import com.resumeflow.service.TemplatePreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 岗位模板 Controller
 */
@Tag(name = "岗位模板接口")
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class ApplicationTemplateController {

    private final ApplicationTemplateService templateService;
    private final TemplatePreviewService previewService;

    @Operation(summary = "查询所有岗位模板")
    @GetMapping
    public Result<List<ApplicationTemplateDTO>> list() {
        return Result.success(templateService.listTemplates());
    }

    @Operation(summary = "新建岗位模板")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ApplicationTemplateDTO dto) {
        return Result.success(templateService.createTemplate(dto));
    }

    @Operation(summary = "更新岗位模板")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ApplicationTemplateDTO dto) {
        templateService.updateTemplate(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除岗位模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return Result.success();
    }

    @Operation(summary = "模板简历预览：按模板配置生成完整简历（含专业技能模块）")
    @GetMapping("/{id}/resume-preview")
    public Result<Map<String, Object>> resumePreview(@PathVariable Long id) {
        return Result.success(previewService.preview(id));
    }
}
