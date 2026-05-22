package org.example.back.dto;

import lombok.Data;

@Data
public class UserProfileDTO {
    private String anonymizedId;
    private String statusFondUrgenta;
    private String statusGradIndatorare;
    private String statusSigurantaLunara;
}