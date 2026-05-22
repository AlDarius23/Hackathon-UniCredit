package org.example.back.service;

import org.example.back.dto.UserProfileDTO;
import org.example.back.model.User;
import org.example.back.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class ProfileScoringService {

    @Autowired
    private UserRepository userRepository;

    public User calculateAndSaveRadar(User user) {
        if ("slaba".equalsIgnoreCase(user.getSituatieEconomii())) {
            user.setFondUrgenta(35);
        } else if ("medie".equalsIgnoreCase(user.getSituatieEconomii())) {
            user.setFondUrgenta(65);
        } else {
            user.setFondUrgenta(95);
        }
        if ("masina".equalsIgnoreCase(user.getObiectiv()) && "slaba".equalsIgnoreCase(user.getSituatieEconomii())) {
            user.setGradIndatorare(80);
        } else if ("casa".equalsIgnoreCase(user.getObiectiv())) {
            user.setGradIndatorare(60);
        } else {
            user.setGradIndatorare(25);
        }

        if ("ridicata".equalsIgnoreCase(user.getTolerantaRisc())) {
            user.setSigurantaLunara(40);
        } else {
            user.setSigurantaLunara(75);
        }

        return userRepository.save(user);
    }

    public UserProfileDTO getAnonymizedProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilizatorul nu a fost găsit"));

        UserProfileDTO dto = new UserProfileDTO();

        dto.setAnonymizedId(UUID.randomUUID().toString());

        dto.setStatusFondUrgenta(user.getFondUrgenta() < 50 ? "Nivel Scăzut" : "Nivel Optim");
        dto.setStatusGradIndatorare(user.getGradIndatorare() > 70 ? "Risc Ridicat" : "Risc Controlat");
        dto.setStatusSigurantaLunara(user.getSigurantaLunara() < 50 ? "Vulnerabil" : "Stabil");

        return dto;
    }
}