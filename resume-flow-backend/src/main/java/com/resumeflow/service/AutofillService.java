package com.resumeflow.service;

import com.resumeflow.dto.AutofillMatchRequest;
import com.resumeflow.entity.AutofillLog;
import com.resumeflow.repository.AutofillLogRepository;
import com.resumeflow.security.SecurityUtils;
import com.resumeflow.vo.AutofillMatchResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 自动填充 Service（调用匹配 + 记录日志）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutofillService {

    private final FieldMatchingService fieldMatchingService;
    private final AutofillLogRepository autofillLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * 执行匹配
     */
    @Transactional
    public AutofillMatchResponse match(AutofillMatchRequest request, HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getCurrentUserId();

        AutofillMatchResponse response = fieldMatchingService.match(request);

        // 记录日志（不记录简历内容，仅记录匹配统计）
        AutofillLog logEntity = new AutofillLog();
        logEntity.setUserId(userId);
        logEntity.setTemplateId(request.getTemplateId());
        logEntity.setPageUrl(request.getPageUrl());
        logEntity.setPageTitle(request.getPageTitle());
        logEntity.setTotalFields(request.getFields() != null ? request.getFields().size() : 0);
        logEntity.setMatchedCount(response.getMatches() != null ? response.getMatches().size() : 0);
        logEntity.setFilledCount(response.getMatches() != null ? response.getMatches().size() : 0);
        logEntity.setSkippedCount(response.getSkipped() != null ? response.getSkipped().size() : 0);
        logEntity.setSensitiveCount((int) (response.getSkipped() == null ? 0 : response.getSkipped()
                .stream().filter(s -> Boolean.TRUE.equals(s.getSensitive())).count()));
        logEntity.setDetailJson(serializeDetail(response));
        logEntity.setClientIp(getClientIp(httpRequest));
        logEntity.setFillType("manual".equals(request.getFillType()) ? "manual" : "auto");
        logEntity.setStatus("SUCCESS");

        autofillLogRepository.save(logEntity);
        log.info("自动填充匹配完成: userId={}, total={}, matched={}, skipped={}",
                userId, logEntity.getTotalFields(), logEntity.getMatchedCount(), logEntity.getSkippedCount());

        return response;
    }

    /**
     * 查询填充日志（分页）
     */
    public Page<AutofillLog> getLogs(int page, int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size);
        return autofillLogRepository.findByUserIdAndDeletedFalseOrderByCreateTimeDesc(userId, pageable);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String serializeDetail(AutofillMatchResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.warn("序列化 autofill detail 失败", e);
            return "{\"error\":\"detail serialize failed\"}";
        }
    }
}
