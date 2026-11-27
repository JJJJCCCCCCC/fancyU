package com.sysudate.api.matching;

import com.sysudate.api.common.api.LegacyApiException;
import com.sysudate.api.matching.MatchingRepository.ExchangeState;
import com.sysudate.api.matching.MatchingRepository.MatchRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("spring.datasource.url")
public class MatchingService {
    private final MatchingRepository matches;

    public MatchingService(MatchingRepository matches) {
        this.matches = matches;
    }

    public List<Map<String, Object>> history(int accountId) {
        return matches.findForAccount(accountId).stream()
            .map(this::view)
            .toList();
    }

    public ExchangeState agree(int matchId, int accountId) {
        if (matches.findOwned(matchId, accountId).isEmpty()) {
            throw new LegacyApiException(
                HttpStatus.NOT_FOUND,
                404,
                "匹配记录不存在");
        }
        return matches.agree(matchId, accountId);
    }

    public void withdraw(int accountId) {
        matches.withdraw(accountId);
    }

    private Map<String, Object> view(MatchRecord match) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", match.id());
        result.put("survey_type", match.surveyType());
        result.put("match_round", match.matchRound());
        result.put("created_at", match.createdAt());
        result.put("contact_exchanged", match.contactExchanged());
        result.put("user_agreed", match.userAgreed());
        result.put("match_agreed", match.matchAgreed());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("person", match.person());
        data.put(
            "contact",
            match.contactExchanged()
                ? matches.contactForAccount(match.matchedAccountId())
                : null);
        result.put("match_data", data);
        return result;
    }
}
