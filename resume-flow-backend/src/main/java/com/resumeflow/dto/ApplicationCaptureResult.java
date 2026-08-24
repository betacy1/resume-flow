package com.resumeflow.dto;

import lombok.Data;

/**
 * 插件采集结果
 */
@Data
public class ApplicationCaptureResult {

    /** created=新增 / updated=更新已有记录 / need_confirm=置信度低需用户确认 */
    private String action;
    private Long recordId;
    private String applyStatus;
    /** 命中的已有记录摘要（need_confirm 时用于插件展示） */
    private String matchedSummary;
    private String message;

    public static ApplicationCaptureResult created(Long id, String status) {
        ApplicationCaptureResult r = new ApplicationCaptureResult();
        r.setAction("created");
        r.setRecordId(id);
        r.setApplyStatus(status);
        r.setMessage("已新增投递记录");
        return r;
    }

    public static ApplicationCaptureResult updated(Long id, String status) {
        ApplicationCaptureResult r = new ApplicationCaptureResult();
        r.setAction("updated");
        r.setRecordId(id);
        r.setApplyStatus(status);
        r.setMessage("已记录，本次已更新最近访问时间");
        return r;
    }

    public static ApplicationCaptureResult needConfirm(String summary) {
        ApplicationCaptureResult r = new ApplicationCaptureResult();
        r.setAction("need_confirm");
        r.setMatchedSummary(summary);
        r.setMessage("检测到可能的投递信息，是否保存到投递表？");
        return r;
    }
}
