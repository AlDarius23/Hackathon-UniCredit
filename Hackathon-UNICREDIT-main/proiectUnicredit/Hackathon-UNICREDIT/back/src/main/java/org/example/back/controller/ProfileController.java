package org.example.back.controller;

import org.example.back.dto.UserProfileDTO;
import org.example.back.model.User;
import org.example.back.service.ProfileScoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    @Autowired
    private ProfileScoringService profileScoringService;

    @PostMapping
    public User createProfile(@RequestBody User user) {
        return profileScoringService.calculateAndSaveRadar(user);
    }

    @GetMapping("/{id}/anonymized")
    public UserProfileDTO getAnonymized(@PathVariable Long id) {
        return profileScoringService.getAnonymizedProfile(id);
    }
}