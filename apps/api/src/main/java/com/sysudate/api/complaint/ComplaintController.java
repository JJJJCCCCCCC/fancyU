package com.sysudate.api.complaint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.common.security.CurrentAccount;
import com.sysudate.api.complaint.ComplaintRepository.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ComplaintController {
    private static final Set<String> STATUSES = Set.of(
        "pending",
        "processing",
        "resolved",
        "dismissed");

    private final ObjectProvider<ComplaintRepository> complaints;

    public ComplaintController(
        ObjectProvider<ComplaintRepository> complaints
    ) {
        this.complaints = complaints;
    }

    @PostMapping("/api/complaint")
    public Map<String, Object> submit(
        @AuthenticationPrincipal CurrentAccount principal,
        @Valid @RequestBody SubmitRequest request
    ) {
        repository().submit(
            principal == null ? null : principal.accountId(),
            request.targetType() == null ? "other" : request.targetType(),
            request.targetId(),
            request.reason(),
            request.description(),
            request.contactEmail());
        return Map.of(
            "code", 200,
            "msg", "投诉已提交，我们会尽快处理");
    }

    @GetMapping("/admin/complaints")
    public Map<String, Object> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit
    ) {
        if (status != null && !STATUSES.contains(status)) {
            throw badRequest("投诉状态无效");
        }
        int safePage = Math.max(1, page);
        int safeLimit = Math.clamp(limit, 1, 100);
        Page result = repository().list(status, safePage, safeLimit);
        return Map.of(
            "code", 200,
            "data", Map.of(
                "complaints", result.complaints(),
                "total", result.total(),
                "page", result.page(),
                "limit", result.pageSize()));
    }

    @PostMapping("/admin/complaint/{id}/status")
    public Map<String, Object> update(
        @PathVariable int id,
        @Valid @RequestBody StatusRequest request
    ) {
        if (!STATUSES.contains(request.status())) {
            throw badRequest("投诉状态无效");
        }
        if (!repository().updateStatus(
            id,
            request.status(),
            request.adminReply())) {
            throw new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "投诉记录不存在");
        }
        return Map.of("code", 200, "msg", "状态已更新");
    }

    private ComplaintRepository repository() {
        ComplaintRepository repository = complaints.getIfAvailable();
        if (repository == null) {
            throw new LegacyApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                503,
                "投诉服务暂不可用");
        }
        return repository;
    }

    private LegacyApiException badRequest(String message) {
        return new LegacyApiException(
            HttpStatus.BAD_REQUEST,
            400,
            message);
    }

    public record SubmitRequest(
        @JsonProperty("target_type") String targetType,
        @JsonProperty("target_id") String targetId,
        @NotBlank String reason,
        String description,
        @JsonProperty("contact_email") String contactEmail
    ) {
    }

    public record StatusRequest(
        @NotBlank String status,
        @JsonProperty("admin_reply") String adminReply
    ) {
    }
}
