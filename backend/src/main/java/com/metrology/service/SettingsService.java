package com.metrology.service;

import com.metrology.dto.SettingsDto;
import com.metrology.entity.Device;
import com.metrology.entity.User;
import com.metrology.entity.UserSettings;
import com.metrology.repository.DeviceRepository;
import com.metrology.repository.UserRepository;
import com.metrology.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    public SettingsDto getSettings(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserSettings s = settingsRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefault(user.getId()));
        SettingsDto dto = new SettingsDto();
        dto.setWarningDays(s.getWarningDays());
        dto.setExpiredDays(s.getExpiredDays());
        return dto;
    }

    public SettingsDto saveSettings(String username, SettingsDto dto) {
        User user = userRepository.findByUsername(username).orElseThrow();
        UserSettings s = settingsRepository.findByUserId(user.getId())
                .orElseGet(() -> createDefault(user.getId()));
        s.setWarningDays(dto.getWarningDays());
        s.setExpiredDays(dto.getExpiredDays());
        settingsRepository.save(s);

        // recalculate validity for all devices with new settings
        List<Device> devices = deviceRepository.findAll();
        for (Device d : devices) {
            String[] metrics = calculateMetrics(d.getCalDate(), dto.getWarningDays(), dto.getExpiredDays());
            d.setValidity(metrics[0]);
            d.setDaysPassed(Integer.parseInt(metrics[1]));
        }
        deviceRepository.saveAll(devices);
        return dto;
    }

    private String[] calculateMetrics(LocalDate calDate, int warningDays, int expiredDays) {
        if (calDate == null) return new String[]{"有效", "0"};
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(calDate, today);
        if (days < 0) days = 0;
        String validity;
        if (days >= expiredDays) validity = "失效";
        else if (days >= warningDays) validity = "即将过期";
        else validity = "有效";
        return new String[]{validity, String.valueOf(days)};
    }

    private UserSettings createDefault(Long userId) {
        UserSettings s = new UserSettings();
        s.setUserId(userId);
        s.setWarningDays(315);
        s.setExpiredDays(360);
        return settingsRepository.save(s);
    }
}
