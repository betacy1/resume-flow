package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.service.UserCustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "自定义字段管理接口")
@RestController
@RequestMapping("/api/custom-fields")
@RequiredArgsConstructor
public class UserCustomFieldController {

    private final UserCustomFieldService userCustomFieldService;

    @Operation(summary = "查询字段列表")
    @GetMapping
    public Result<List<UserCustomFieldDTO>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) Boolean enabled,
                                                 @RequestParam(required = false) Long templateId) {
        return Result.success(userCustomFieldService.list(keyword, category, enabled, templateId));
    }

    @Operation(summary = "新增字段")
    @PostMapping
    public Result<Long> create(@RequestBody UserCustomFieldDTO dto) {
        return Result.success(userCustomFieldService.create(dto));
    }

    @Operation(summary = "编辑字段")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserCustomFieldDTO dto) {
        userCustomFieldService.update(id, dto);
        return Result.success();
    }

    @Operation(summary = "删除字段")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userCustomFieldService.delete(id);
        return Result.success();
    }

    @Operation(summary = "启用或禁用字段")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {
        userCustomFieldService.setEnabled(id, enabled);
        return Result.success();
    }

    @Operation(summary = "为字段追加匹配关键词（插件手动绑定页面字段）")
    @PostMapping("/{id}/keywords")
    public Result<Void> addKeyword(@PathVariable Long id, @RequestParam String keyword) {
        userCustomFieldService.addMatchKeyword(id, keyword);
        return Result.success();
    }
}
