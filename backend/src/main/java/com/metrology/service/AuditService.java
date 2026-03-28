package com.metrology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metrology.dto.DeviceDto;
import com.metrology.entity.AuditRecord;
import com.metrology.entity.AuditWorkflowStep;
import com.metrology.entity.UserSettings;
import com.metrology.repository.AuditRecordRepository;
import com.metrology.repository.AuditWorkflowStepRepository;
import com.metrology.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRecordRepository auditRepository;
    private final AuditWorkflowStepRepository workflowRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper;

    /** 普通用户提交新增申请 */
    public AuditRecord submitCreate(String username, DeviceDto dto) {
        AuditRecord record = new AuditRecord();
        record.setType("CREATE");
        record.setEntityType("DEVICE");
        record.setSubmittedBy(username);
        try { record.setNewData(objectMapper.writeValueAsString(dto)); } catch (Exception ignored) {}
        if (dto.getRemark() != null) record.setRemark(dto.getRemark());
        return auditRepository.save(record);
    }

    /** 普通用户提交修改申请 */
    public AuditRecord submitUpdate(String username, Long id, DeviceDto dto) {
        AuditRecord record = new AuditRecord();
        record.setType("UPDATE");
        record.setEntityType("DEVICE");
        record.setEntityId(id);
        record.setSubmittedBy(username);
        // 序列化原始快照
        deviceRepository.findById(id).ifPresent(device -> {
            try {
                UserSettings settings = deviceService.getSettings(username);
                DeviceDto original = deviceService.toDto(device, settings);
                record.setOriginalData(objectMapper.writeValueAsString(original));
            } catch (Exception ignored) {}
        });
        try { record.setNewData(objectMapper.writeValueAsString(dto)); } catch (Exception ignored) {}
        return auditRepository.save(record);
    }

    /** 普通用户提交删除申请 */
    public AuditRecord submitDelete(String username, Long id) {
        AuditRecord record = new AuditRecord();
        record.setType("DELETE");
        record.setEntityType("DEVICE");
        record.setEntityId(id);
        record.setSubmittedBy(username);
        deviceRepository.findById(id).ifPresent(device -> {
            try {
                UserSettings settings = deviceService.getSettings(username);
                DeviceDto original = deviceService.toDto(device, settings);
                record.setOriginalData(objectMapper.writeValueAsString(original));
            } catch (Exception ignored) {}
        });
        return auditRepository.save(record);
    }

    /** 管理员审批通过 */
    @Transactional
    public AuditRecord approve(String adminUsername, Long auditId, String remark) {
        AuditRecord record = auditRepository.findById(auditId)
                .orElseThrow(() -> new IllegalArgumentException("审核记录不存在"));
        if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("该记录已被处理");
        }
        // 执行实际操作
        try {
            if ("DEVICE".equals(record.getEntityType())) {
                switch (record.getType()) {
                    case "CREATE" -> {
                        DeviceDto dto = objectMapper.readValue(record.getNewData(), DeviceDto.class);
                        DeviceDto created = deviceService.createDevice(adminUsername, dto);
                        record.setEntityId(created.getId());
                    }
                    case "UPDATE" -> {
                        DeviceDto dto = objectMapper.readValue(record.getNewData(), DeviceDto.class);
                        deviceService.updateDevice(adminUsername, record.getEntityId(), dto);
                    }
                    case "DELETE" -> deviceService.deleteDevice(record.getEntityId());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("执行操作失败: " + e.getMessage(), e);
        }
        record.setStatus("APPROVED");
        record.setApprovedBy(adminUsername);
        record.setApprovedAt(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) record.setRemark(remark);
        return auditRepository.save(record);
    }

    /** 管理员驳回 */
    public AuditRecord reject(String adminUsername, Long auditId, String reason) {
        AuditRecord record = auditRepository.findById(auditId)
                .orElseThrow(() -> new IllegalArgumentException("审核记录不存在"));
        if (!"PENDING".equals(record.getStatus())) {
            throw new IllegalStateException("该记录已被处理");
        }
        record.setStatus("REJECTED");
        record.setApprovedBy(adminUsername);
        record.setApprovedAt(LocalDateTime.now());
        record.setRejectReason(reason);
        return auditRepository.save(record);
    }

    public List<AuditRecord> getPending() {
        return auditRepository.findByStatusOrderBySubmittedAtDesc("PENDING");
    }

    public List<AuditRecord> getMyRecords(String username) {
        return auditRepository.findBySubmittedByOrderBySubmittedAtDesc(username);
    }

    public Page<AuditRecord> getAll(Pageable pageable) {
        return auditRepository.findAllByOrderBySubmittedAtDesc(pageable);
    }

    public AuditRecord getById(Long id) {
        return auditRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("审核记录不存在"));
    }

    public long getPendingCount() {
        return auditRepository.countByStatus("PENDING");
    }

    // ── 审批流程配置 ──────────────────────────────

    public List<AuditWorkflowStep> getWorkflow(String moduleName) {
        return workflowRepository.findByModuleNameOrderByStepOrderAsc(moduleName);
    }

    @Transactional
    public List<AuditWorkflowStep> saveWorkflow(String moduleName, List<Map<String, Object>> steps) {
        workflowRepository.deleteByModuleName(moduleName);
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> s = steps.get(i);
            AuditWorkflowStep step = new AuditWorkflowStep();
            step.setModuleName(moduleName);
            step.setStepOrder(i + 1);
            step.setStepName((String) s.get("stepName"));
            step.setApproverType((String) s.getOrDefault("approverType", "ROLE"));
            step.setApproverValue((String) s.getOrDefault("approverValue", "ADMIN"));
            workflowRepository.save(step);
        }
        return workflowRepository.findByModuleNameOrderByStepOrderAsc(moduleName);
    }
}
