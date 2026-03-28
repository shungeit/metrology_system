package com.metrology.service;

import com.metrology.dto.DashboardStats;
import com.metrology.dto.DeviceDto;
import com.metrology.dto.PageResult;
import com.metrology.entity.Device;
import com.metrology.entity.UserSettings;
import com.metrology.entity.User;
import com.metrology.repository.DeviceRepository;
import com.metrology.repository.UserRepository;
import com.metrology.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.DateUtil;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final UserSettingsRepository settingsRepository;

    @Value("${upload.path:/app/uploads}")
    private String uploadPath;

    UserSettings getSettings(String username) {
        return userRepository.findByUsername(username)
                .flatMap(u -> settingsRepository.findByUserId(u.getId()))
                .orElseGet(() -> {
                    UserSettings s = new UserSettings();
                    s.setWarningDays(315);
                    s.setExpiredDays(360);
                    return s;
                });
    }

    /** 全量列表（供校准管理、导出等使用） */
    public List<DeviceDto> getDevices(String username, String search, String assetNo, String serialNo,
                                       String dept, String validity, String responsiblePerson, String useStatus) {
        UserSettings settings = getSettings(username);

        // 非管理员且设置了部门的用户，强制只看本部门
        String effectiveDept = dept;
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!"ADMIN".equals(user.getRole()) && user.getDepartment() != null && !user.getDepartment().isBlank()) {
                effectiveDept = user.getDepartment();
            }
        }

        List<Device> devices = deviceRepository.findWithFilters(
                (search == null || search.isBlank()) ? null : search,
                (assetNo == null || assetNo.isBlank()) ? null : assetNo,
                (serialNo == null || serialNo.isBlank()) ? null : serialNo,
                (effectiveDept == null || effectiveDept.isBlank()) ? null : effectiveDept,
                null,
                (useStatus == null || useStatus.isBlank()) ? null : useStatus
        );
        List<DeviceDto> result = new ArrayList<>();
        for (Device d : devices) {
            DeviceDto dto = toDto(d, settings);
            boolean validityMatch = validity == null || validity.isBlank() || validity.equals(dto.getValidity());
            boolean personMatch = responsiblePerson == null || responsiblePerson.isBlank()
                    || responsiblePerson.equals(dto.getResponsiblePerson());
            if (validityMatch && personMatch) result.add(dto);
        }
        result.sort(Comparator.comparing(dto -> dto.getNextDate() != null ? dto.getNextDate() : "9999-99-99"));
        return result;
    }

    /** 分页列表（设备台账专用） */
    public PageResult<DeviceDto> getDevicesPaged(String username, String search, String assetNo, String serialNo,
                                                  String dept, String validity, String responsiblePerson,
                                                  String useStatus, int page, int size) {
        List<DeviceDto> all = getDevices(username, search, assetNo, serialNo, dept, validity, responsiblePerson, useStatus);
        long total = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 1;
        int safePage = Math.max(1, Math.min(page, Math.max(totalPages, 1)));
        List<DeviceDto> content = size > 0
                ? all.stream().skip((long) (safePage - 1) * size).limit(size).collect(Collectors.toList())
                : all;
        return new PageResult<>(content, total, totalPages, safePage, size);
    }

    public DeviceDto createDevice(String username, DeviceDto dto) {
        UserSettings settings = getSettings(username);
        Device device = fromDto(dto);
        device.setCreatedBy(username);
        recalcMetrics(device, settings);
        return toDto(deviceRepository.save(device), settings);
    }

    public DeviceDto updateDevice(String username, Long id, DeviceDto dto) {
        UserSettings settings = getSettings(username);
        Device device = deviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("设备不存在"));
        updateFromDto(device, dto);
        recalcMetrics(device, settings);
        return toDto(deviceRepository.save(device), settings);
    }

    public void deleteDevice(Long id) {
        deviceRepository.deleteById(id);
    }

    public DashboardStats getDashboardStats(String username) {
        UserSettings settings = getSettings(username);
        List<Device> all = deviceRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        // 普通用户仅看本部门数据
        Optional<User> userOpt = userRepository.findByUsername(username);
        boolean isAdmin = userOpt.map(u -> "ADMIN".equals(u.getRole())).orElse(false);
        String userDept = (!isAdmin && userOpt.isPresent()) ? userOpt.get().getDepartment() : null;
        if (userDept != null && !userDept.isBlank()) {
            final String dept = userDept;
            all = all.stream().filter(d -> dept.equals(d.getDept())).collect(Collectors.toList());
        }

        DashboardStats stats = new DashboardStats();
        stats.setTotal(all.size());

        // 失效/即将过期/有效 仅统计使用状态为"正常"的设备
        List<Device> normalDevices = all.stream()
                .filter(d -> "正常".equals(d.getUseStatus()))
                .collect(Collectors.toList());

        long expired = 0, warning = 0, valid = 0, dueThisMonth = 0;
        Map<String, long[]> deptMap = new LinkedHashMap<>();

        for (Device d : all) {
            if (d.getNextDate() != null &&
                !d.getNextDate().isBefore(startOfMonth) &&
                !d.getNextDate().isAfter(endOfMonth)) {
                dueThisMonth++;
            }
        }

        for (Device d : normalDevices) {
            String[] metrics = calculateMetrics(d.getCalDate(), d.getCycle(),
                    settings.getWarningDays(), settings.getExpiredDays());
            String v = metrics[0];
            if ("失效".equals(v)) expired++;
            else if ("即将过期".equals(v)) warning++;
            else valid++;

            String dept = d.getDept() != null && !d.getDept().isBlank() ? d.getDept() : "未分配";
            deptMap.computeIfAbsent(dept, k -> new long[4]);
            deptMap.get(dept)[0]++;
            if ("失效".equals(v)) deptMap.get(dept)[3]++;
            else if ("即将过期".equals(v)) deptMap.get(dept)[2]++;
            else deptMap.get(dept)[1]++;
        }

        stats.setExpired(expired);
        stats.setWarning(warning);
        stats.setValid(valid);
        stats.setDueThisMonth(dueThisMonth);

        List<Map<String, Object>> deptStats = new ArrayList<>();
        deptMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("dept", entry.getKey());
                    item.put("total", entry.getValue()[0]);
                    item.put("valid", entry.getValue()[1]);
                    item.put("warning", entry.getValue()[2]);
                    item.put("expired", entry.getValue()[3]);
                    deptStats.add(item);
                });
        stats.setDeptStats(deptStats);

        LocalDate sixMonthsAgo = today.minusMonths(6).withDayOfMonth(1);
        List<Object[]> rawTrend = (userDept != null && !userDept.isBlank())
                ? deviceRepository.countByCalDateMonthAndDept(sixMonthsAgo, userDept)
                : deviceRepository.countByCalDateMonth(sixMonthsAgo);
        Map<String, Long> trendMap = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate m = today.minusMonths(i).withDayOfMonth(1);
            trendMap.put(m.getYear() + "-" + String.format("%02d", m.getMonthValue()), 0L);
        }
        for (Object[] row : rawTrend) {
            int yr = ((Number) row[0]).intValue();
            int mo = ((Number) row[1]).intValue();
            long cnt = ((Number) row[2]).longValue();
            String key = yr + "-" + String.format("%02d", mo);
            if (trendMap.containsKey(key)) trendMap.put(key, cnt);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        trendMap.forEach((k, v) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("month", k);
            entry.put("count", v);
            trend.add(entry);
        });
        stats.setMonthlyTrend(trend);
        return stats;
    }

    // ── 设备台账导出 ──────────────────────────────
    public byte[] exportExcel(String username, String search, String assetNo, String serialNo,
                               String dept, String validity, String useStatus) throws IOException {
        List<DeviceDto> dtos = getDevices(username, search, assetNo, serialNo, dept, validity, null, useStatus);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("设备台账");
            String[] headers = {
                "仪器名称","计量编号","资产编号","出厂编号","ABC分类","使用部门","设备位置",
                "制造厂","设备型号","使用责任人","采购时间","采购价格(元)","使用年限(年)",
                "检定周期(月)","校准时间","下次校准","有效性","校准结果判定",
                "分度值","测试范围","允许误差","使用状态","备注"
            };
            CellStyle hStyle = wb.createCellStyle();
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hStyle);
                sheet.setColumnWidth(i, 4200);
            }
            int rowNum = 1;
            for (DeviceDto d : dtos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s(d.getName()));
                row.createCell(1).setCellValue(s(d.getMetricNo()));
                row.createCell(2).setCellValue(s(d.getAssetNo()));
                row.createCell(3).setCellValue(s(d.getSerialNo()));
                row.createCell(4).setCellValue(s(d.getAbcClass()));
                row.createCell(5).setCellValue(s(d.getDept()));
                row.createCell(6).setCellValue(s(d.getLocation()));
                row.createCell(7).setCellValue(s(d.getManufacturer()));
                row.createCell(8).setCellValue(s(d.getModel()));
                row.createCell(9).setCellValue(s(d.getResponsiblePerson()));
                if (d.getPurchaseDate() != null && !d.getPurchaseDate().isBlank()) {
                    Cell dc = row.createCell(10);
                    dc.setCellValue(LocalDate.parse(d.getPurchaseDate()));
                    dc.setCellStyle(dateStyle);
                } else { row.createCell(10).setCellValue(""); }
                if (d.getPurchasePrice() != null) row.createCell(11).setCellValue(d.getPurchasePrice()); else row.createCell(11).setCellValue("");
                if (d.getServiceLife() != null) row.createCell(12).setCellValue(d.getServiceLife()); else row.createCell(12).setCellValue("");
                if (d.getCycle() != null) row.createCell(13).setCellValue(d.getCycle()); else row.createCell(13).setCellValue("");
                if (d.getCalDate() != null && !d.getCalDate().isBlank()) {
                    Cell dc = row.createCell(14);
                    dc.setCellValue(LocalDate.parse(d.getCalDate()));
                    dc.setCellStyle(dateStyle);
                } else { row.createCell(14).setCellValue(""); }
                if (d.getNextDate() != null && !d.getNextDate().isBlank()) {
                    Cell dc = row.createCell(15);
                    dc.setCellValue(LocalDate.parse(d.getNextDate()));
                    dc.setCellStyle(dateStyle);
                } else { row.createCell(15).setCellValue(""); }
                row.createCell(16).setCellValue(s(d.getValidity()));
                row.createCell(17).setCellValue(s(d.getCalibrationResult()));
                row.createCell(18).setCellValue(s(d.getGraduationValue()));
                row.createCell(19).setCellValue(s(d.getTestRange()));
                row.createCell(20).setCellValue(s(d.getAllowableError()));
                row.createCell(21).setCellValue(s(d.getUseStatus()));
                row.createCell(22).setCellValue(s(d.getRemark()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── 校准管理导出 ──────────────────────────────
    public byte[] exportCalibration(String username, String search, String dept,
                                     String validity, String responsiblePerson) throws IOException {
        List<DeviceDto> dtos = getDevices(username, search, null, null, dept, validity, responsiblePerson, null);
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("校准管理");
            String[] headers = {
                "仪器名称","计量编号","使用部门","使用责任人",
                "上次校准","下次校准","检定周期(月)","有效性","校准结果判定","使用状态","备注"
            };
            CellStyle hStyle = wb.createCellStyle();
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hStyle);
                sheet.setColumnWidth(i, 4200);
            }
            int rowNum = 1;
            for (DeviceDto d : dtos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(s(d.getName()));
                row.createCell(1).setCellValue(s(d.getMetricNo()));
                row.createCell(2).setCellValue(s(d.getDept()));
                row.createCell(3).setCellValue(s(d.getResponsiblePerson()));
                row.createCell(4).setCellValue(s(d.getCalDate()));
                row.createCell(5).setCellValue(s(d.getNextDate()));
                if (d.getCycle() != null) row.createCell(6).setCellValue(d.getCycle()); else row.createCell(6).setCellValue("");
                row.createCell(7).setCellValue(s(d.getValidity()));
                row.createCell(8).setCellValue(s(d.getCalibrationResult()));
                row.createCell(9).setCellValue(s(d.getUseStatus()));
                row.createCell(10).setCellValue(s(d.getRemark()));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── 导入模板 ──────────────────────────────────
    public byte[] getTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("导入模板");
            // Identical column structure as export
            String[] headers = {
                "仪器名称*","计量编号*","资产编号","出厂编号","ABC分类","使用部门","设备位置",
                "制造厂","设备型号","使用责任人","采购时间","采购价格(元)","使用年限(年)(自动计算)",
                "检定周期(月)","校准时间","下次校准(自动计算)","有效性(自动计算)","校准结果判定",
                "分度值","测试范围","允许误差","使用状态","备注"
            };

            // Header style (yellow for required, blue for optional)
            CellStyle reqStyle = wb.createCellStyle();
            Font reqFont = wb.createFont(); reqFont.setBold(true); reqStyle.setFont(reqFont);
            reqStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            reqStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle optStyle = wb.createCellStyle();
            Font optFont = wb.createFont(); optFont.setBold(true); optStyle.setFont(optFont);
            optStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            optStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle autoStyle = wb.createCellStyle();
            Font autoFont = wb.createFont(); autoFont.setBold(true); autoStyle.setFont(autoFont);
            autoStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            autoStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));

            // Set that columns 12, 15, 16 (auto-calculated) use autoStyle
            Set<Integer> autoCols = Set.of(12, 15, 16);
            Set<Integer> reqCols = Set.of(0, 1);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(autoCols.contains(i) ? autoStyle : reqCols.contains(i) ? reqStyle : optStyle);
                sheet.setColumnWidth(i, i == 12 || i == 15 || i == 16 ? 5500 : 4200);
            }

            // Example row
            Row ex = sheet.createRow(1);
            ex.createCell(0).setCellValue("数显卡尺(示例)");
            ex.createCell(1).setCellValue("M2024001");
            ex.createCell(2).setCellValue("ZC001");
            ex.createCell(3).setCellValue("SN12345");
            ex.createCell(4).setCellValue("A类");
            ex.createCell(5).setCellValue("生产一部");
            ex.createCell(6).setCellValue("一车间");
            ex.createCell(7).setCellValue("三丰量具");
            ex.createCell(8).setCellValue("CD-20AX");
            ex.createCell(9).setCellValue("张三");
            Cell pdCell = ex.createCell(10);
            pdCell.setCellValue(LocalDate.of(2022, 3, 1));
            pdCell.setCellStyle(dateStyle);
            ex.createCell(11).setCellValue(1200.00);
            ex.createCell(12).setCellValue("(自动计算)");
            ex.createCell(13).setCellValue(12);
            Cell cdCell = ex.createCell(14);
            cdCell.setCellValue(LocalDate.of(2025, 1, 15));
            cdCell.setCellStyle(dateStyle);
            ex.createCell(15).setCellValue("(自动计算)");
            ex.createCell(16).setCellValue("(自动计算)");
            ex.createCell(17).setCellValue("合格");
            ex.createCell(18).setCellValue("0.01mm");
            ex.createCell(19).setCellValue("0-200mm");
            ex.createCell(20).setCellValue("±0.02mm");
            ex.createCell(21).setCellValue("正常");
            ex.createCell(22).setCellValue("示例备注");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── 批量导入 ──────────────────────────────────
    public int importExcel(String username, MultipartFile file) throws IOException {
        UserSettings settings = getSettings(username);
        int count = 0;
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell c = headerRow.getCell(i);
                if (c != null) {
                    // 规范化列名：去掉 * 和 (YYYY-MM-DD) 和 (自动计算) 后缀
                    String key = c.getStringCellValue().trim()
                            .replace("*", "")
                            .replace("(YYYY-MM-DD)", "")
                            .replace("(自动计算)", "")
                            .trim();
                    colMap.put(key, i);
                }
            }
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = getCellStr(row, colMap.get("仪器名称"));
                String metricNo = getCellStr(row, colMap.get("计量编号"));
                if (name == null || name.isBlank() || metricNo == null || metricNo.isBlank()) continue;

                Device d = new Device();
                d.setName(name);
                d.setMetricNo(metricNo);
                d.setAssetNo(getCellStr(row, colMap.get("资产编号")));
                d.setSerialNo(getCellStr(row, colMap.get("出厂编号")));
                d.setAbcClass(getCellStr(row, colMap.get("ABC分类")));
                d.setDept(getCellStr(row, colMap.get("使用部门")));
                d.setLocation(getCellStr(row, colMap.get("设备位置")));
                d.setManufacturer(getCellStr(row, colMap.get("制造厂")));
                d.setModel(getCellStr(row, colMap.get("设备型号")));
                d.setResponsiblePerson(getCellStr(row, colMap.get("使用责任人")));
                d.setGraduationValue(getCellStr(row, colMap.get("分度值")));
                d.setTestRange(getCellStr(row, colMap.get("测试范围")));
                d.setAllowableError(getCellStr(row, colMap.get("允许误差")));
                d.setCalibrationResult(getCellStr(row, colMap.get("校准结果判定")));
                d.setRemark(getCellStr(row, colMap.get("备注")));
                d.setCreatedBy(username);

                String useStatusStr = getCellStr(row, colMap.get("使用状态"));
                if (useStatusStr != null && !useStatusStr.isBlank()) d.setUseStatus(useStatusStr);

                String cycleStr = getCellStr(row, colMap.get("检定周期(月)"));
                d.setCycle(cycleStr != null && !cycleStr.isBlank() ? (int) Double.parseDouble(cycleStr) : 12);

                String calDateStr = getCellStr(row, colMap.get("校准时间"));
                if (calDateStr != null && !calDateStr.isBlank()) {
                    try { d.setCalDate(LocalDate.parse(calDateStr.trim())); } catch (Exception ignored) {}
                }
                String purchaseDateStr = getCellStr(row, colMap.get("采购时间"));
                if (purchaseDateStr != null && !purchaseDateStr.isBlank()) {
                    try { d.setPurchaseDate(LocalDate.parse(purchaseDateStr.trim())); } catch (Exception ignored) {}
                }
                String priceStr = getCellStr(row, colMap.get("采购价格(元)"));
                if (priceStr != null && !priceStr.isBlank()) {
                    try { d.setPurchasePrice(Double.parseDouble(priceStr)); } catch (Exception ignored) {}
                }
                recalcMetrics(d, settings);
                try {
                    deviceRepository.save(d);
                    count++;
                } catch (Exception e) {
                    // skip duplicate or invalid rows
                }
            }
        }
        return count;
    }

    public String saveFile(MultipartFile file) throws IOException {
        File dir = new File(uploadPath);
        if (!dir.exists()) dir.mkdirs();
        String ext = "";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) ext = original.substring(original.lastIndexOf("."));
        String filename = UUID.randomUUID() + ext;
        File dest = new File(dir, filename);
        file.transferTo(dest);
        return "/uploads/" + filename;
    }

    public String[] calculateMetrics(LocalDate calDate, Integer cycle, int warningDays, int expiredDays) {
        if (calDate == null) return new String[]{"有效", "0"};
        LocalDate today = LocalDate.now();
        long daysPassed = ChronoUnit.DAYS.between(calDate, today);
        if (daysPassed < 0) daysPassed = 0;
        String validity;
        if (daysPassed >= expiredDays) validity = "失效";
        else if (daysPassed >= warningDays) validity = "即将过期";
        else validity = "有效";
        return new String[]{validity, String.valueOf(daysPassed)};
    }

    private void recalcMetrics(Device d, UserSettings settings) {
        String[] metrics = calculateMetrics(d.getCalDate(), d.getCycle(),
                settings.getWarningDays(), settings.getExpiredDays());
        d.setValidity(metrics[0]);
        d.setDaysPassed(Integer.parseInt(metrics[1]));
        if (d.getCalDate() != null && d.getCycle() != null) {
            d.setNextDate(d.getCalDate().plusMonths(d.getCycle()));
        }
    }

    DeviceDto toDto(Device d, UserSettings settings) {
        String[] metrics = calculateMetrics(d.getCalDate(), d.getCycle(),
                settings.getWarningDays(), settings.getExpiredDays());
        DeviceDto dto = new DeviceDto();
        dto.setId(d.getId());
        dto.setName(d.getName());
        dto.setMetricNo(d.getMetricNo());
        dto.setAssetNo(d.getAssetNo());
        dto.setSerialNo(d.getSerialNo());
        dto.setAbcClass(d.getAbcClass());
        dto.setDept(d.getDept());
        dto.setLocation(d.getLocation());
        dto.setCycle(d.getCycle());
        dto.setCalDate(d.getCalDate() != null ? d.getCalDate().toString() : null);
        dto.setNextDate(d.getNextDate() != null ? d.getNextDate().toString() : null);
        dto.setValidity(metrics[0]);
        dto.setDaysPassed(Integer.parseInt(metrics[1]));
        dto.setStatus(d.getStatus());
        dto.setRemark(d.getRemark());
        dto.setImagePath(d.getImagePath());
        dto.setImageName(d.getImageName());
        dto.setCertPath(d.getCertPath());
        dto.setCertName(d.getCertName());
        dto.setUseStatus(d.getUseStatus() != null ? d.getUseStatus() : "正常");
        dto.setPurchasePrice(d.getPurchasePrice());
        dto.setPurchaseDate(d.getPurchaseDate() != null ? d.getPurchaseDate().toString() : null);
        dto.setCalibrationResult(d.getCalibrationResult());
        dto.setResponsiblePerson(d.getResponsiblePerson());
        dto.setManufacturer(d.getManufacturer());
        dto.setModel(d.getModel());
        dto.setGraduationValue(d.getGraduationValue());
        dto.setTestRange(d.getTestRange());
        dto.setAllowableError(d.getAllowableError());
        if (d.getPurchaseDate() != null) {
            long years = ChronoUnit.YEARS.between(d.getPurchaseDate(), LocalDate.now());
            dto.setServiceLife((int) Math.max(0, years));
        }
        return dto;
    }

    private Device fromDto(DeviceDto dto) {
        Device d = new Device();
        updateFromDto(d, dto);
        return d;
    }

    private void updateFromDto(Device d, DeviceDto dto) {
        if (dto.getName() != null) d.setName(dto.getName());
        if (dto.getMetricNo() != null) d.setMetricNo(dto.getMetricNo());
        if (dto.getAssetNo() != null) d.setAssetNo(dto.getAssetNo());
        if (dto.getSerialNo() != null) d.setSerialNo(dto.getSerialNo());
        if (dto.getAbcClass() != null) d.setAbcClass(dto.getAbcClass());
        if (dto.getDept() != null) d.setDept(dto.getDept());
        if (dto.getLocation() != null) d.setLocation(dto.getLocation());
        if (dto.getCycle() != null) d.setCycle(dto.getCycle());
        if (dto.getCalDate() != null && !dto.getCalDate().isBlank()) {
            d.setCalDate(LocalDate.parse(dto.getCalDate()));
        } else { d.setCalDate(null); }
        if (dto.getPurchaseDate() != null && !dto.getPurchaseDate().isBlank()) {
            d.setPurchaseDate(LocalDate.parse(dto.getPurchaseDate()));
        } else if (dto.getPurchaseDate() != null) { d.setPurchaseDate(null); }
        if (dto.getRemark() != null) d.setRemark(dto.getRemark());
        if (dto.getStatus() != null) d.setStatus(dto.getStatus());
        if (dto.getUseStatus() != null) d.setUseStatus(dto.getUseStatus());
        if (dto.getImagePath() != null) d.setImagePath(dto.getImagePath());
        if (dto.getImageName() != null) d.setImageName(dto.getImageName());
        if (dto.getCertPath() != null) d.setCertPath(dto.getCertPath());
        if (dto.getCertName() != null) d.setCertName(dto.getCertName());
        if (dto.getPurchasePrice() != null) d.setPurchasePrice(dto.getPurchasePrice());
        if (dto.getCalibrationResult() != null) d.setCalibrationResult(dto.getCalibrationResult());
        if (dto.getResponsiblePerson() != null) d.setResponsiblePerson(dto.getResponsiblePerson());
        if (dto.getManufacturer() != null) d.setManufacturer(dto.getManufacturer());
        if (dto.getModel() != null) d.setModel(dto.getModel());
        if (dto.getGraduationValue() != null) d.setGraduationValue(dto.getGraduationValue());
        if (dto.getTestRange() != null) d.setTestRange(dto.getTestRange());
        if (dto.getAllowableError() != null) d.setAllowableError(dto.getAllowableError());
    }

    private String getCellStr(Row row, Integer col) {
        if (col == null || row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                double v = cell.getNumericCellValue();
                yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case FORMULA -> {
                try {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toLocalDate()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE);
                    }
                    double v = cell.getNumericCellValue();
                    yield v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
                } catch (Exception e) {
                    String s = cell.getStringCellValue().trim();
                    yield s.isEmpty() ? null : s;
                }
            }
            default -> null;
        };
    }

    private String s(String v) { return v != null ? v : ""; }
}
