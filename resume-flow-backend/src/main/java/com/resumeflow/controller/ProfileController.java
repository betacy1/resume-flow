package com.resumeflow.controller;

import com.resumeflow.common.Result;
import com.resumeflow.dto.*;
import com.resumeflow.service.ProfileService;
import com.resumeflow.vo.ProfileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 简历 Profile Controller
 * 聚合：基础信息、教育经历、实习经历、项目经历、技能信息
 */
@Tag(name = "简历 Profile 接口")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    // ========== 基础信息 + 聚合查询 ==========

    @Operation(summary = "查询完整简历信息")
    @GetMapping
    public Result<ProfileVO> getProfile() {
        return Result.success(profileService.getProfile());
    }

    @Operation(summary = "保存/更新简历基础信息")
    @PutMapping
    public Result<Void> saveProfile(@Valid @RequestBody UserProfileDTO dto) {
        profileService.saveOrUpdateProfile(dto);
        return Result.success();
    }

    // ========== 教育经历 ==========

    @Operation(summary = "保存/新增教育经历")
    @PostMapping("/education")
    public Result<Void> saveEducation(@Valid @RequestBody EducationExperienceDTO dto) {
        profileService.saveEducation(dto);
        return Result.success();
    }

    @Operation(summary = "删除教育经历")
    @DeleteMapping("/education/{id}")
    public Result<Void> deleteEducation(@PathVariable Long id) {
        profileService.deleteEducation(id);
        return Result.success();
    }

    // ========== 实习经历 ==========

    @Operation(summary = "保存/新增实习经历")
    @PostMapping("/internship")
    public Result<Void> saveInternship(@Valid @RequestBody InternshipExperienceDTO dto) {
        profileService.saveInternship(dto);
        return Result.success();
    }

    @Operation(summary = "删除实习经历")
    @DeleteMapping("/internship/{id}")
    public Result<Void> deleteInternship(@PathVariable Long id) {
        profileService.deleteInternship(id);
        return Result.success();
    }

    // ========== 项目经历 ==========

    @Operation(summary = "保存/新增项目经历")
    @PostMapping("/project")
    public Result<Void> saveProject(@Valid @RequestBody ProjectExperienceDTO dto) {
        profileService.saveProject(dto);
        return Result.success();
    }

    @Operation(summary = "删除项目经历")
    @DeleteMapping("/project/{id}")
    public Result<Void> deleteProject(@PathVariable Long id) {
        profileService.deleteProject(id);
        return Result.success();
    }

    // ========== 技能信息 ==========

    @Operation(summary = "保存/新增技能信息")
    @PostMapping("/skill")
    public Result<Void> saveSkill(@Valid @RequestBody SkillProfileDTO dto) {
        profileService.saveSkill(dto);
        return Result.success();
    }

    @Operation(summary = "删除技能信息")
    @DeleteMapping("/skill/{id}")
    public Result<Void> deleteSkill(@PathVariable Long id) {
        profileService.deleteSkill(id);
        return Result.success();
    }

    // ========== 奖项证书 ==========

    @Operation(summary = "保存/新增奖项证书")
    @PostMapping("/award")
    public Result<Void> saveAward(@Valid @RequestBody AwardCertificateDTO dto) {
        profileService.saveAward(dto);
        return Result.success();
    }

    @Operation(summary = "删除奖项证书")
    @DeleteMapping("/award/{id}")
    public Result<Void> deleteAward(@PathVariable Long id) {
        profileService.deleteAward(id);
        return Result.success();
    }
}
