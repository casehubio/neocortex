package io.casehub.neocortex.knowledge;

import java.util.List;

public interface ResearchSessionService {

    ResearchSession create(String name, String criteria, String tenantId);

    void pause(String sessionId);

    void resume(String sessionId);

    void complete(String sessionId);

    List<ResearchSession> listActive(String tenantId);

    default List<ResearchSession> list(String tenantId) {
        return listActive(tenantId);
    }


    ResearchSession get(String sessionId);
}
