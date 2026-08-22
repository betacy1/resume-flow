package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.ContentVariantDTO;
import com.resumeflow.entity.ContentVariant;
import com.resumeflow.service.ContentVariantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内容版本 Controller（实习/项目/素材的多受众×多长度版本管理）
 */
@Tag(name = "内容版本接口")
@RestController
@RequestMapping("/api/content-variants")
@RequiredArgsConstructor
public class ContentVariantController {

    private final ContentVariantService contentVariantService;

    @Operation(summary = "查询内容版本（可按来源筛选）")
    @GetMapping
    public Result<List<ContentVariant>> list(@RequestParam(required = false) String sourceType,
                                             @RequestParam(required = false) Long sourceId) {
        if (sourceType != null && sourceId != null) {
            return Result.success(contentVariantService.listBySource(sourceType, sourceId));
        }
        return Result.success(contentVariantService.listAll());
    }

    @Operation(summary = "保存/新增内容版本")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody ContentVariantDTO dto) {
        contentVariantService.save(dto);
        return Result.success();
    }

    @Operation(summary = "删除内容版本")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contentVariantService.delete(id);
        return Result.success();
    }
}
