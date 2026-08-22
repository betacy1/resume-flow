package com.resumeflow.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日期多格式解析与格式化服务
 * 所有经历/项目只存标准日期（yyyy-MM-dd 或 yyyy-MM），按页面需要动态格式化。
 *
 * 支持解析的输入格式（16 种）：
 * yyyy-MM-dd / yyyy-M-d / yyyy-MM / yyyy-M / yyyy.MM / yyyy.M / yyyy.MM.dd / yyyy.M.d /
 * yyyy年MM月dd日 / yyyy年M月d日 / yyyy年MM月 / yyyy年M月 / yyyy/MM/dd / yyyy/M/d / yyyy/MM / yyyy/M
 */
@Service
public class DateFormatService {

    /** 通用解析正则：年 + 可选月 + 可选日，分隔符支持 - . / 年 月 */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{4})\\s*[-./年]?\\s*(?:(\\d{1,2})\\s*[-./月]?\\s*(?:(\\d{1,2})\\s*日?)?)?");

    /** 年份/月份拆分 token */
    public static final String TOKEN_YEAR = "yyyy";
    public static final String TOKEN_MONTH = "MM";
    public static final String TOKEN_MONTH_NO_PAD = "M";

    /**
     * 解析任意支持格式的日期，返回标准值：含日 → yyyy-MM-dd，仅月 → yyyy-MM，仅年 → yyyy；解析失败返回 null
     */
    public String normalize(String input) {
        LocalDate date = parse(input);
        if (date == null) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(input == null ? "" : input.trim());
        boolean hasMonth = matcher.find() && matcher.group(2) != null;
        boolean hasDay = matcher.group(3) != null;
        if (!hasMonth) {
            return String.valueOf(date.getYear());
        }
        if (hasDay) {
            return date.toString();
        }
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }

    /**
     * 解析为 LocalDate（月级默认 1 日），解析失败返回 null
     */
    public LocalDate parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(input.trim());
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 1;
        int day = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 1;
        try {
            return LocalDate.of(year, Math.min(Math.max(month, 1), 12), Math.min(Math.max(day, 1), 31));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 标准值是否为月级（yyyy-MM）
     */
    public boolean isMonthOnly(String stdDate) {
        return stdDate != null && stdDate.matches("\\d{4}-\\d{2}");
    }

    /**
     * 按目标格式 token 格式化。目标格式比存储粒度细时用默认值补全（日补 1）。
     *
     * @param stdDate 标准日期（yyyy-MM-dd / yyyy-MM / yyyy）
     * @param fmt     目标格式，如 yyyy-MM-dd、yyyy年M月、yyyy、MM 等
     */
    public String format(String stdDate, String fmt) {
        LocalDate date = parse(stdDate);
        if (date == null || fmt == null) {
            return stdDate;
        }
        int y = date.getYear();
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        String mm2 = String.format("%02d", m);
        String dd2 = String.format("%02d", d);
        return switch (fmt) {
            case "yyyy-MM-dd" -> String.format("%04d-%s-%s", y, mm2, dd2);
            case "yyyy-M-d" -> String.format("%d-%d-%d", y, m, d);
            case "yyyy-MM" -> String.format("%04d-%s", y, mm2);
            case "yyyy-M" -> String.format("%d-%d", y, m);
            case "yyyy.MM.dd" -> String.format("%04d.%s.%s", y, mm2, dd2);
            case "yyyy.M.d" -> String.format("%d.%d.%d", y, m, d);
            case "yyyy.MM" -> String.format("%04d.%s", y, mm2);
            case "yyyy.M" -> String.format("%d.%d", y, m);
            case "yyyy年MM月dd日" -> String.format("%04d年%s月%s日", y, mm2, dd2);
            case "yyyy年M月d日" -> String.format("%d年%d月%d日", y, m, d);
            case "yyyy年MM月" -> String.format("%04d年%s月", y, mm2);
            case "yyyy年M月" -> String.format("%d年%d月", y, m);
            case "yyyy/MM/dd" -> String.format("%04d/%s/%s", y, mm2, dd2);
            case "yyyy/M/d" -> String.format("%d/%d/%d", y, m, d);
            case "yyyy/MM" -> String.format("%04d/%s", y, mm2);
            case "yyyy/M" -> String.format("%d/%d", y, m);
            case TOKEN_YEAR -> String.valueOf(y);
            case TOKEN_MONTH -> mm2;
            case TOKEN_MONTH_NO_PAD -> String.valueOf(m);
            default -> stdDate;
        };
    }

    /**
     * 日期范围格式化，分隔符 " - "，如 2024.09 - 2027.06
     */
    public String formatRange(String startStd, String endStd, String fmt) {
        String start = startStd == null ? "" : format(startStd, fmt);
        String end = endStd == null ? "至今" : format(endStd, fmt);
        return start + " - " + end;
    }

    /**
     * 根据页面字段上下文探测目标日期格式
     *
     * @param inputType   input 的 type（date / month / text ...）
     * @param placeholder 占位符文本
     * @param label       label / 附近文本
     */
    public String detectFormat(String inputType, String placeholder, String label) {
        if ("date".equalsIgnoreCase(inputType)) {
            return "yyyy-MM-dd";
        }
        if ("month".equalsIgnoreCase(inputType)) {
            return "yyyy-MM";
        }
        String p = placeholder == null ? "" : placeholder.toLowerCase();
        if (p.contains("yyyy-mm-dd") || p.contains("yyyy/mm/dd") || p.contains("yyyy.mm.dd")) {
            return "yyyy-MM-dd";
        }
        // 占位符示例形如 2026-5-8：允许不补 0
        if (p.matches(".*\\d{4}-\\d{1}-\\d{1,2}.*")) {
            return "yyyy-M-d";
        }
        if (p.contains("yyyy-mm") || p.contains("yyyy/mm") || p.contains("yyyy.mm")) {
            return "yyyy-MM";
        }
        if (p.matches(".*\\d{4}-\\d{1}.*")) {
            return "yyyy-M";
        }
        if (p.contains("年月日")) {
            return "yyyy年M月d日";
        }
        if (p.contains("年月")) {
            return "yyyy年M月";
        }
        // 年月拆分字段：单独的"年"或"月"输入框
        String l = label == null ? "" : label;
        if (isYearField(l)) {
            return TOKEN_YEAR;
        }
        if (isMonthField(l)) {
            return TOKEN_MONTH_NO_PAD;
        }
        return "yyyy-MM";
    }

    /**
     * 文本是否表示日期类字段
     */
    public boolean isDateField(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("时间") || lower.contains("日期") || lower.contains("年份")
                || lower.contains("年月") || lower.contains("start date") || lower.contains("end date")
                || lower.contains("start") || lower.contains("毕业");
    }

    /**
     * 是否为开始时间语义
     */
    public boolean isStartSemantics(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("起始时间") || lower.contains("开始时间") || lower.contains("入职时间")
                || lower.contains("入学时间") || lower.contains("start date") || lower.contains("start");
    }

    /**
     * 是否为结束时间语义
     */
    public boolean isEndSemantics(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("结束时间") || lower.contains("离职时间") || lower.contains("毕业时间")
                || lower.contains("预计毕业") || lower.contains("end date") || lower.contains("end");
    }

    private boolean isYearField(String label) {
        // label 形如"年"、"毕业年"，排除同时含"月"的完整日期
        return (label.endsWith("年") || label.contains("年份")) && !label.contains("月");
    }

    private boolean isMonthField(String label) {
        return (label.endsWith("月") || label.contains("月份")) && !label.contains("年");
    }
}
