package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.AutofillMatchRequest;
import com.resumeflow.entity.AutofillLog;
import com.resumeflow.service.AutofillService;
import com.resumeflow.vo.AutofillMatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 自动填充 Controller
 */
@Tag(name = "自动填充接口")
@RestController
@RequestMapping("/api/autofill")
@RequiredArgsConstructor
public class AutofillController {

    private final AutofillService autofillService;

    @Operation(summary = "字段匹配接口")
    @PostMapping("/match")
    public Result<AutofillMatchResponse> match(@Valid @RequestBody AutofillMatchRequest request,
                                               HttpServletRequest httpRequest) {
        return Result.success(autofillService.match(request, httpRequest));
    }

    @Operation(summary = "查询填充日志")
    @GetMapping("/logs")
    public Result<Page<AutofillLog>> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(autofillService.getLogs(page, size));
    }
}
