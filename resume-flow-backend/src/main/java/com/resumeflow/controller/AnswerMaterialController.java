package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.AnswerMaterialDTO;
import com.resumeflow.service.AnswerMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 素材库 Controller
 */
@Tag(name = "素材库接口")
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class AnswerMaterialController {

    private final AnswerMaterialService materialService;

    @Operation(summary = "查询素材列表（可按类型筛选）")
    @GetMapping
    public Result<List<AnswerMaterialDTO>> list(@RequestParam(required = false) String materialType,
                                                @RequestParam(required = false) Long templateId) {
        return Result.success(materialService.listMaterials(materialType, templateId));
    }

    @Operation(summary = "新建素材")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AnswerMaterialDTO dto) {
        return Result.success(materialService.createMaterial(dto));
    }

    @Operation(summary = "更新素材")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AnswerMaterialDTO dto) {
        materialService.updateMaterial(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除素材")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.deleteMaterial(id);
        return Result.success();
    }
}
