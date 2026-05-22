package org.example.back.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "curated_faq")
@Data
public class CuratedFAQ {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String question;
    @Column(columnDefinition = "TEXT")
    private String answer;
}