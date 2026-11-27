package com.sysudate.api.complaint;

import java.util.List;
import java.util.Map;

public interface ComplaintRepository {
    void submit(
        Integer reporterAccountId,
        String targetType,
        String targetId,
        String reason,
        String description,
        String contactEmail);

    Page list(String status, int page, int pageSize);

    boolean updateStatus(int id, String status, String adminReply);

    record Page(
        List<Map<String, Object>> complaints,
        long total,
        int page,
        int pageSize
    ) {
    }
}
