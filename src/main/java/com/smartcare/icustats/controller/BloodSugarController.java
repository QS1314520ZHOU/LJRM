package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.BloodSugarPageData;
import com.smartcare.icustats.service.BloodSugarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/blood-sugar")
public class BloodSugarController {

    private final BloodSugarService bloodSugarService;

    public BloodSugarController(BloodSugarService bloodSugarService) {
        this.bloodSugarService = bloodSugarService;
    }

    @GetMapping("/patient/{pid}")
    public ResponseEntity<Map<String, Object>> getPatientData(@PathVariable String pid) {
        if (pid == null || pid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Patient ID is required"));
        }

        BloodSugarPageData data = bloodSugarService.getPageData(pid.trim());
        if (data == null) {
            return ResponseEntity.ok(Collections.singletonMap("data", null));
        }
        return ResponseEntity.ok(Collections.singletonMap("data", data));
    }
}
