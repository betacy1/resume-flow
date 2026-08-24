package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.TemplateExperienceConfigDTO;
import com.resumeflow.service.TemplateExperienceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模板-经历关系配置 Controller：控制各模板下经历的展示与自动填充策略
 */
@Tag(name = "模板经历配置接口")
@RestController
@RequestMapping("/api/template-configs")
@RequiredArgsConstructor
public class TemplateExperienceConfigController {

    private final TemplateExperienceConfigService configService;

    @Operation(summary = "查询模板下的经历配置")
    @GetMapping
    public Result<List<TemplateExperienceConfigDTO>> list(@RequestParam Long templateId) {
        return Result.success(configService.listByTemplate(templateId));
    }

    @Operation(summary = "批量保存模板下的经历配置")
    @PutMapping("/{templateId}")
    public Result<Void> save(@PathVariable Long templateId, @RequestBody List<TemplateExperienceConfigDTO> dtos) {
        configService.saveByTemplate(templateId, dtos);
        return Result.success();
    }
}
