package com.metrology.dto;

import lombok.Data;

@Data
public class SettingsDto {
    private Integer warningDays;
    private Integer expiredDays;
}
