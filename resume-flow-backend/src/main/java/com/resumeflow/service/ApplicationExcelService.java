package com.resumeflow.service;

import com.resumeflow.entity.ApplicationRecord;
import com.resumeflow.repository.ApplicationRecordRepository;
import com.resumeflow.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 投递信息表 Excel 导入导出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationExcelService {

    /** 导出表头（固定顺序） */
    private static final String[] EXPORT_HEADERS = {
            "批次", "来源类型", "类别", "状态", "公司/单位", "机构/部门", "岗位", "岗位方向", "企业性质",
            "当前阶段", "投递渠道", "官网", "公众号", "招聘系统链接", "写简历网址",
            "简历修改时间", "最近访问时间", "投递时间", "截止时间", "备注", "限制说明",
    };

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationRecordService recordService;
    private final ApplicationRecordRepository recordRepository;

    // ==================== 导出 ====================

    /** 导出当前用户投递信息表：先在内存生成完整文件，成功后才写响应（冻结首行、状态列筛选、日期 yyyy-MM-dd HH:mm） */
    @Transactional
    public void export(HttpServletResponse response) {
        List<ApplicationRecord> records = recordService.listAllOfCurrentUser();

        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("投递信息表");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-MM-dd HH:mm"));
            CellStyle linkStyle = wb.createCellStyle();
            Font linkFont = wb.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            linkStyle.setFont(linkFont);

            Row header = sheet.createRow(0);
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.min(255 * 20, 18 * 256));
            }
            sheet.setColumnWidth(4, 28 * 256);
            sheet.setColumnWidth(13, 40 * 256);
            sheet.setColumnWidth(14, 40 * 256);

            int rowIdx = 1;
            for (ApplicationRecord r : records) {
                Row row = sheet.createRow(rowIdx++);
                setText(row, 0, r.getBatchName());
                setText(row, 1, r.getSourceType());
                setText(row, 2, r.getCategoryType());
                setText(row, 3, r.getApplyStatus());
                setText(row, 4, r.getCompanyName());
                setText(row, 5, r.getOrganizationName());
                setText(row, 6, r.getPositionName());
                setText(row, 7, r.getPositionDirection());
                setText(row, 8, r.getCompanyNature());
                setText(row, 9, r.getCurrentStage());
                setText(row, 10, r.getApplicationChannel());
                setLink(row, 11, r.getOfficialWebsite(), linkStyle);
                setText(row, 12, r.getPublicAccount());
                setLink(row, 13, r.getRecruitmentUrl(), linkStyle);
                setLink(row, 14, r.getResumeEditUrl(), linkStyle);
                setDate(row, 15, r.getResumeModifiedAt(), dateStyle);
                setDate(row, 16, r.getLastVisitedAt(), dateStyle);
                setDate(row, 17, r.getAppliedAt(), dateStyle);
                setDate(row, 18, r.getDeadlineAt(), dateStyle);
                setText(row, 19, r.getRemark());
                setText(row, 20, r.getWarningNote());
            }

            // 冻结首行 + 状态列筛选（自动筛选覆盖整表）
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIdx - 1), 0, EXPORT_HEADERS.length - 1));

            wb.write(bos);
            bytes = bos.toByteArray();
        } catch (Exception e) {
            log.error("导出投递信息表失败", e);
            throw new RuntimeException("导出投递信息表失败：" + e.getMessage(), e);
        }

        // 全部生成成功后才设置响应头并写出，避免异常时响应已提交
        String fileName = "ResumeFlow_投递信息表_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setContentLength(bytes.length);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        try (OutputStream out = response.getOutputStream()) {
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            log.error("写出导出文件失败", e);
            throw new RuntimeException("写出导出文件失败：" + e.getMessage(), e);
        }
    }

    private void setText(Row row, int col, String value) {
        if (value != null && !value.isEmpty()) {
            row.createCell(col).setCellValue(value);
        }
    }

    private void setLink(Row row, int col, String url, CellStyle linkStyle) {
        if (url == null || url.isEmpty()) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(url);
        cell.setCellStyle(linkStyle);
        try {
            CreationHelper helper = row.getSheet().getWorkbook().getCreationHelper();
            Hyperlink link = helper.createHyperlink(
                    url.startsWith("http") ? HyperlinkType.URL : HyperlinkType.DOCUMENT);
            link.setAddress(url);
            cell.setHyperlink(link);
        } catch (Exception ignored) {
            // 非法 URL 时保留纯文本
        }
    }

    private void setDate(Row row, int col, LocalDateTime time, CellStyle dateStyle) {
        if (time == null) {
            return;
        }
        Cell cell = row.createCell(col);
        cell.setCellValue(java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant()));
        cell.setCellStyle(dateStyle);
    }

    // ==================== 导入 ====================

    /**
     * 导入 Excel：按 公司/单位 + 机构/部门 + 岗位 去重，存在则更新，不存在则新增（渠道 Excel导入）。
     */
    @Transactional
    public Map<String, Object> importExcel(MultipartFile file) {
        Long userId = SecurityUtils.getCurrentUserId();
        recordService.ensureInitialized(userId);
        int created = 0;
        int updated = 0;
        int skipped = 0;

        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 中没有工作表");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            Map<String, Integer> headerMap = new LinkedHashMap<>();
            if (headerRow != null) {
                for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                    String name = cellString(headerRow.getCell(i)).trim();
                    if (!name.isEmpty()) {
                        headerMap.put(name, i);
                    }
                }
            }
            Integer companyCol = findColumn(headerMap, "公司/单位", "公司", "单位名称", "公司名称");
            if (companyCol == null) {
                throw new IllegalArgumentException("未找到「公司/单位」列，请检查表头");
            }

            DataFormatter formatter = new DataFormatter();
            List<ApplicationRecord> existing = recordRepository.findByUserIdAndDeletedFalse(userId);
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String company = cellString(row.getCell(companyCol)).trim();
                if (company.isEmpty()) {
                    skipped++;
                    continue;
                }
                String org = cellString(row, headerMap, "机构/部门", "机构", "部门").trim();
                String position = cellString(row, headerMap, "岗位", "岗位名称", "职位名称").trim();
                ApplicationRecord record = existing.stream()
                        .filter(r -> company.equalsIgnoreCase(safe(r.getCompanyName())))
                        .filter(r -> org.equalsIgnoreCase(safe(r.getOrganizationName())))
                        .filter(r -> position.equalsIgnoreCase(safe(r.getPositionName())))
                        .findFirst().orElse(null);
                boolean isNew = record == null;
                if (isNew) {
                    record = new ApplicationRecord();
                    record.setUserId(userId);
                    record.setCompanyName(company);
                    record.setOrganizationName(org);
                    record.setPositionName(position);
                    Integer max = recordRepository.findMaxSortOrder(userId);
                    record.setSortOrder((max == null ? 0 : max) + 1);
                }
                fillFromRow(record, row, headerMap, formatter);
                recordRepository.save(record);
                if (isNew) {
                    created++;
                    existing.add(record);
                } else {
                    updated++;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入投递信息表失败", e);
            throw new IllegalArgumentException("导入失败：" + e.getMessage(), e);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }

    private void fillFromRow(ApplicationRecord r, Row row, Map<String, Integer> headerMap, DataFormatter fmt) {
        String batch = cellString(row, headerMap, "批次");
        String status = cellString(row, headerMap, "状态", "投递状态");
        if (!batch.trim().isEmpty()) {
            r.setBatchName(batch.trim());
        } else if (r.getBatchName() == null) {
            r.setBatchName("2027秋招");
        }
        if (!status.trim().isEmpty()) {
            r.setApplyStatus(status.trim());
        } else if (r.getApplyStatus() == null) {
            r.setApplyStatus("未投");
        }
        setIfPresent(r::setSourceType, cellString(row, headerMap, "来源类型"));
        setIfPresent(r::setCategoryType, cellString(row, headerMap, "类别"));
        setIfPresent(r::setPositionDirection, cellString(row, headerMap, "岗位方向"));
        setIfPresent(r::setCompanyNature, cellString(row, headerMap, "企业性质"));
        setIfPresent(r::setCurrentStage, cellString(row, headerMap, "当前阶段"));
        setIfPresent(r::setPriority, cellString(row, headerMap, "优先级"));
        setIfPresent(r::setCity, cellString(row, headerMap, "城市", "工作城市"));
        String channel = cellString(row, headerMap, "投递渠道");
        if (!channel.trim().isEmpty()) {
            r.setApplicationChannel(channel.trim());
        } else if (r.getApplicationChannel() == null) {
            r.setApplicationChannel("Excel导入");
        }
        setIfPresent(r::setOfficialWebsite, cellString(row, headerMap, "官网"));
        setIfPresent(r::setPublicAccount, cellString(row, headerMap, "公众号"));
        setIfPresent(r::setRecruitmentUrl, cellString(row, headerMap, "招聘系统链接", "招聘官网"));
        setIfPresent(r::setApplicationUrl, cellString(row, headerMap, "投递链接", "实际投递链接"));
        setIfPresent(r::setResumeEditUrl, cellString(row, headerMap, "写简历网址", "简历编辑网址"));
        setIfPresent(r::setRemark, cellString(row, headerMap, "备注"));
        setIfPresent(r::setWarningNote, cellString(row, headerMap, "限制说明"));
        LocalDateTime appliedAt = parseDate(row, headerMap, fmt, "投递时间");
        if (appliedAt != null) {
            r.setAppliedAt(appliedAt);
        }
        LocalDateTime deadline = parseDate(row, headerMap, fmt, "截止时间");
        if (deadline != null) {
            r.setDeadlineAt(deadline);
        }
        LocalDateTime resumeModified = parseDate(row, headerMap, fmt, "简历修改时间");
        if (resumeModified != null) {
            r.setResumeModifiedAt(resumeModified);
            if (r.getResumeModifiedSource() == null) {
                r.setResumeModifiedSource("manual");
            }
        }
        LocalDateTime lastVisited = parseDate(row, headerMap, fmt, "最近访问时间");
        if (lastVisited != null) {
            r.setLastVisitedAt(lastVisited);
        }
    }

    private void setIfPresent(java.util.function.Consumer<String> setter, String value) {
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
        }
    }

    private LocalDateTime parseDate(Row row, Map<String, Integer> headerMap, DataFormatter fmt, String... names) {
        Integer col = findColumn(headerMap, names);
        if (col == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String text = fmt.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return null;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm", "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy年MM月dd日"};
        for (String p : patterns) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern(p, Locale.CHINA);
                if (p.length() <= 11) {
                    return java.time.LocalDate.parse(text, f).atStartOfDay();
                }
                return LocalDateTime.parse(text, f);
            } catch (Exception ignored) {
                // 尝试下一个格式
            }
        }
        return null;
    }

    private String cellString(Row row, Map<String, Integer> headerMap, String... names) {
        Integer col = findColumn(headerMap, names);
        if (col == null) {
            return "";
        }
        return cellString(row.getCell(col));
    }

    private String cellString(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().format(DATE_TIME_FMT)
                    : new DataFormatter().formatCellValue(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield new DataFormatter().formatCellValue(cell);
                }
            }
            default -> "";
        };
    }

    private Integer findColumn(Map<String, Integer> headerMap, String... names) {
        for (String name : names) {
            Integer col = headerMap.get(name);
            if (col != null) {
                return col;
            }
        }
        return null;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
