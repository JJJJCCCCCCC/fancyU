package com.sysudate.api.admin;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AdminRepository {
    UserPage users(UserQuery query);

    Optional<Map<String, Object>> user(String serialNumber);

    Map<String, Object> statistics();

    boolean deleteSurvey(String serialNumber);

    List<Map<String, Object>> chatList();

    Optional<Map<String, Object>> chat(String id);

    void saveChat(
        String id,
        String title,
        List<Map<String, Object>> messages);

    void deleteChat(String id);

    void audit(
        String actor,
        String action,
        String resourceType,
        String resourceId,
        String requestId);

    record UserQuery(
        int page,
        int pageSize,
        String sort,
        String order,
        String search,
        String sex,
        String campus,
        String grade,
        Boolean joinMatching
    ) {
    }

    record UserPage(
        List<Map<String, Object>> rows,
        long total,
        int page,
        int pageSize
    ) {
    }
}
