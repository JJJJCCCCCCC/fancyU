package com.sysudate.api.complaint;

import com.sysudate.api.complaint.ComplaintRepository.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty("spring.datasource.url")
public class JdbcComplaintRepository implements ComplaintRepository {
    private final JdbcTemplate jdbc;

    public JdbcComplaintRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void submit(
        Integer reporterAccountId,
        String targetType,
        String targetId,
        String reason,
        String description,
        String contactEmail
    ) {
        jdbc.update(
            """
                INSERT INTO complaints (
                    reporter_account_id, target_type, target_id, reason,
                    description, contact_email
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            reporterAccountId,
            targetType,
            targetId,
            reason,
            description,
            contactEmail);
    }

    @Override
    public Page list(String status, int page, int pageSize) {
        String where = status == null ? "" : " WHERE status = ?";
        List<Object> parameters = new ArrayList<>();
        if (status != null) {
            parameters.add(status);
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM complaints" + where,
            Long.class,
            parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM complaints" + where
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
            parameters.toArray());
        return new Page(
            rows,
            total == null ? 0 : total,
            page,
            pageSize);
    }

    @Override
    public boolean updateStatus(
        int id,
        String status,
        String adminReply
    ) {
        return jdbc.update(
            """
                UPDATE complaints
                SET status = ?, admin_reply = ?
                WHERE id = ?
                """,
            status,
            adminReply,
            id) == 1;
    }
}
