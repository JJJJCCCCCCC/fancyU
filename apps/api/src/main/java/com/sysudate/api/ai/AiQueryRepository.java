package com.sysudate.api.ai;

import java.util.Map;

public interface AiQueryRepository {
    Map<String, Object> execute(String intent, Map<String, Object> parameters);
}
