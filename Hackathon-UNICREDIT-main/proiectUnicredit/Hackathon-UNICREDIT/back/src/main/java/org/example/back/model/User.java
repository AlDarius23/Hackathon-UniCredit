package org.example.back.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;

    private String obiectiv;
    private String tolerantaRisc;
    private String situatieEconomii;

    private Integer fondUrgenta;
    private Integer gradIndatorare;
    private Integer sigurantaLunara;
}