package com.sysudate.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {
    @GetMapping("/health")
    public HealthStatus health() {
        return new HealthStatus("UP");
    }

    public record HealthStatus(String status) {
    }
}
