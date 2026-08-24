package com.resumeflow.service;

import com.resumeflow.entity.EmergencyContact;
import com.resumeflow.entity.FamilyMember;
import com.resumeflow.entity.UserProfile;
import com.resumeflow.repository.EmergencyContactRepository;
import com.resumeflow.repository.FamilyMemberRepository;
import com.resumeflow.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 新用户注册默认字段结构（标准字段体系）：
 * 每个用户注册后即拥有家庭成员（父亲/母亲两条空模板）、紧急联系人、简历基础信息等标准字段结构，
 * 内容为空由用户自行填写；空字段不参与自动填充，也不允许用其他字段兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultStructureService {

    private final UserProfileRepository userProfileRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final EmergencyContactRepository emergencyContactRepository;

    @Transactional
    public void initDefaultStructure(Long userId) {
        // 基础信息空壳（管理后台/插件可直接编辑保存）
        if (userProfileRepository.findByUserIdAndDeletedFalse(userId).isEmpty()) {
            UserProfile profile = new UserProfile();
            profile.setUserId(userId);
            userProfileRepository.save(profile);
        }
        // 家庭成员默认两条空模板：父亲、母亲
        if (familyMemberRepository.countByUserIdAndDeletedFalse(userId) == 0) {
            int order = 0;
            for (String relation : new String[]{"父亲", "母亲"}) {
                FamilyMember member = new FamilyMember();
                member.setUserId(userId);
                member.setRelation(relation);
                member.setSortOrder(order++);
                familyMemberRepository.save(member);
            }
        }
        // 紧急联系人默认一条空记录
        if (emergencyContactRepository.countByUserIdAndDeletedFalse(userId) == 0) {
            EmergencyContact contact = new EmergencyContact();
            contact.setUserId(userId);
            emergencyContactRepository.save(contact);
        }
        log.info("新用户默认字段结构初始化完成 (userId={})", userId);
    }
}
