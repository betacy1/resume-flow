package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.service.PluginFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 插件字段接口：插件侧新增 / 编辑 / 删除（逻辑删除）/ 启停自定义字段。
 * 写操作响应携带 profileVersion / dataHash / updatedAt，插件据此对齐本地缓存；
 * 编辑提交 version 落后于服务端时返回 HTTP 409 与服务端最新字段。
 */
@Tag(name = "插件字段接口")
@RestController
@RequestMapping("/api/plugin/fields")
@RequiredArgsConstructor
public class PluginFieldController {

    private final PluginFieldService pluginFieldService;

    @Operation(summary = "插件新增字段")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody UserCustomFieldDTO dto) {
        return Result.success(pluginFieldService.create(dto));
    }

    @Operation(summary = "插件编辑字段（乐观锁：version 落后返回 409）")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody UserCustomFieldDTO dto) {
        return Result.success(pluginFieldService.update(id, dto));
    }

    @Operation(summary = "插件删除字段（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        return Result.success(pluginFieldService.delete(id));
    }

    @Operation(summary = "插件启用/禁用字段")
    @PatchMapping("/{id}/toggle")
    public Result<Map<String, Object>> toggle(@PathVariable Long id) {
        return Result.success(pluginFieldService.toggle(id));
    }
}
