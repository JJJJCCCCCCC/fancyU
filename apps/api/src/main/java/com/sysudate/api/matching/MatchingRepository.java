package com.sysudate.api.matching;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MatchingRepository {
    List<MatchRecord> findForAccount(int accountId);

    Optional<MatchRecord> findOwned(int matchId, int accountId);

    ExchangeState agree(int matchId, int accountId);

    String contactForAccount(int accountId);

    void withdraw(int accountId);

    record MatchRecord(
        int id,
        int accountId,
        int matchedAccountId,
        String surveyType,
        int matchRound,
        Map<String, Object> matchData,
        boolean userAgreed,
        boolean matchAgreed,
        boolean contactExchanged,
        Instant createdAt,
        Map<String, Object> person
    ) {
    }

    record ExchangeState(
        boolean userAgreed,
        boolean matchAgreed,
        boolean contactExchanged
    ) {
    }
}
