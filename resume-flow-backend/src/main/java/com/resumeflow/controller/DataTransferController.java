package com.resumeflow.controller;

import com.resumeflow.common.BusinessException;
import com.resumeflow.common.Result;
import com.resumeflow.dto.AnswerMaterialDTO;
import com.resumeflow.dto.UserCustomFieldDTO;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.service.AnswerMaterialService;
import com.resumeflow.service.SyncService;
import com.resumeflow.service.UserCustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导入导出接口
 * 导出：当前用户全部简历配置（与 /api/sync/full 相同结构的 JSON）；
 * 导入：按 fieldKey 合并（自定义字段）与 类型+标题 合并（开放题素材），
 * mode=merge 仅新增、mode=overwrite 覆盖已有同键内容。
 */
@Slf4j
@Tag(name = "数据导入导出接口")
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataTransferController {

    private final SyncService syncService;
    private final UserCustomFieldService customFieldService;
    private final AnswerMaterialService materialService;

    @Operation(summary = "导出当前用户全部简历配置为 JSON")
    @GetMapping("/export")
    public Result<Map<String, Object>> exportData() {
        return Result.success(syncService.fullPayload());
    }

    @Operation(summary = "导入 JSON 数据（mode: merge 仅新增 / overwrite 覆盖同键内容）")
    @PostMapping("/import")
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> importData(@RequestBody Map<String, Object> request) {
        Map<String, Object> payload = request.get("payload") instanceof Map
                ? (Map<String, Object>) request.get("payload")
                : request;
        String mode = String.valueOf(request.getOrDefault("mode", "merge"));
        boolean overwrite = "overwrite".equalsIgnoreCase(mode);
        Long userId = SecurityUtils.getCurrentUserId();

        int added = 0;
        int updated = 0;
        int skipped = 0;

        // 自定义字段：按 fieldKey 合并，避免重复
        Object fieldsObj = payload.get("customFields");
        if (fieldsObj instanceof List<?> fieldList) {
            Map<String, UserCustomFieldDTO> existingByKey = new LinkedHashMap<>();
            for (UserCustomFieldDTO f : customFieldService.list(null, null, null, null)) {
                existingByKey.put(f.getFieldKey(), f);
            }
            for (Object item : fieldList) {
                try {
                    UserCustomFieldDTO dto = convert(item, UserCustomFieldDTO.class);
                    if (dto.getFieldKey() == null || dto.getFieldKey().isBlank()) {
                        skipped++;
                        continue;
                    }
                    UserCustomFieldDTO existing = existingByKey.get(dto.getFieldKey());
                    if (existing == null) {
                        dto.setId(null);
                        customFieldService.create(dto);
                        added++;
                    } else if (overwrite) {
                        dto.setVersion(existing.getVersion());
                        customFieldService.update(existing.getId(), dto);
                        updated++;
                    } else {
                        skipped++;
                    }
                } catch (BusinessException e) {
                    skipped++;
                }
            }
        }

        // 开放题素材：按 类型 + 标题 合并
        Object materialsObj = payload.get("materials");
        if (materialsObj instanceof List<?> materialList) {
            List<AnswerMaterialDTO> existingMaterials = materialService.listMaterials(null, null);
            for (Object item : materialList) {
                try {
                    AnswerMaterialDTO dto = convert(item, AnswerMaterialDTO.class);
                    if (dto.getTitle() == null || dto.getMaterialType() == null) {
                        skipped++;
                        continue;
                    }
                    AnswerMaterialDTO existing = existingMaterials.stream()
                            .filter(m -> dto.getMaterialType().equals(m.getMaterialType())
                                    && dto.getTitle().equals(m.getTitle()))
                            .findFirst().orElse(null);
                    if (existing == null) {
                        dto.setId(null);
                        materialService.createMaterial(dto);
                        added++;
                    } else if (overwrite) {
                        materialService.updateMaterial(existing.getId(), dto);
                        updated++;
                    } else {
                        skipped++;
                    }
                } catch (BusinessException e) {
                    skipped++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("updated", updated);
        result.put("skipped", skipped);
        log.info("用户 {} 数据导入完成：新增 {}，覆盖 {}，跳过 {}（mode={}）", userId, added, updated, skipped, mode);
        return Result.success(result);
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper CONVERTER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private <T> T convert(Object item, Class<T> type) {
        return CONVERTER.convertValue(item, type);
    }
}
