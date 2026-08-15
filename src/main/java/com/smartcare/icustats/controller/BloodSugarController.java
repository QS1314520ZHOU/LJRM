package com.smartcare.icustats.controller;

import com.smartcare.icustats.dto.BloodSugarPageData;
import com.smartcare.icustats.service.BloodSugarService;
import com.smartcare.icustats.util.ShanghaiTimeRangeUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    public ResponseEntity<?> getPatientData(
            @PathVariable String pid,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime) {

        if (pid == null || pid.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Patient ID is required"));
        }

        // Validate time range params
        boolean hasStart = startTime != null;
        boolean hasEnd = endTime != null;
        if (hasStart != hasEnd) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "startTime and endTime must both be provided or both omitted"));
        }
        if (hasStart && hasEnd && !ShanghaiTimeRangeUtils.isValidRange(startTime, endTime)) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "startTime must be before endTime"));
        }

        try {
            BloodSugarPageData data = bloodSugarService.getPageData(pid.trim(), startTime, endTime);
            if (data == null) {
                return ResponseEntity.ok(Collections.singletonMap("data", null));
            }
            return ResponseEntity.ok(Collections.singletonMap("data", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}
