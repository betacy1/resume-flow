package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.SkillProfileDTO;
import com.resumeflow.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 专业技能 Controller
 */
@Tag(name = "专业技能接口")
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "查询专业技能（七个分组 + 各模板技能内容版本）")
    @GetMapping
    public Result<Map<String, Object>> list() {
        return Result.success(skillService.getBundle());
    }

    @Operation(summary = "批量保存专业技能并重新生成各模板技能版本")
    @PutMapping
    public Result<Void> save(@RequestBody SkillSaveRequest request) {
        skillService.saveBundle(request.getSkills(), request.getKeywords());
        return Result.success();
    }

    @Operation(summary = "手动重新生成技能 100/200/300/500 字与完整版本")
    @PostMapping("/regenerate")
    public Result<Void> regenerate() {
        skillService.regenerate();
        return Result.success();
    }

    @Data
    public static class SkillSaveRequest {
        private List<SkillProfileDTO> skills;
        /** 技能关键词（公共字段，同步更新到全部模板） */
        private String keywords;
    }
}
