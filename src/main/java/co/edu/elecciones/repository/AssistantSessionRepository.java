package co.edu.elecciones.repository;

import co.edu.elecciones.domain.AssistantSession;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssistantSessionRepository extends StatementRepository<AssistantSession> {

    @Query("""
            select s
            from AssistantSession s
            left join fetch s.election e
            where s.sessionKey = :sessionKey
              and s.status = 'ACTIVE'
            """)
    Optional<AssistantSession> selectActiveBySessionKey(@Param("sessionKey") UUID sessionKey);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AssistantSession s
            set s.status = 'CLOSED',
                s.updatedAt = current_timestamp
            where s.sessionKey = :sessionKey
            """)
    int closeBySessionKey(@Param("sessionKey") UUID sessionKey);
}
