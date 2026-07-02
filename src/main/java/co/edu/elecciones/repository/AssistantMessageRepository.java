package co.edu.elecciones.repository;

import co.edu.elecciones.domain.AssistantMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AssistantMessageRepository extends StatementRepository<AssistantMessage> {

    @Query("""
            select m
            from AssistantMessage m
            join fetch m.session s
            where s.sessionKey = :sessionKey
            order by m.createdAt desc, m.id desc
            """)
    List<AssistantMessage> selectRecentBySessionKey(@Param("sessionKey") UUID sessionKey, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AssistantMessage m
            set m.helpful = :helpful,
                m.feedbackComment = :comment,
                m.updatedAt = :updatedAt
            where m.id = :messageId
              and m.role = 'ASSISTANT'
              and m.session.sessionKey = :sessionKey
            """)
    int updateFeedback(@Param("sessionKey") UUID sessionKey,
                       @Param("messageId") Long messageId,
                       @Param("helpful") Boolean helpful,
                       @Param("comment") String comment,
                       @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AssistantMessage m
            where m.session.id in (
                select s.id from AssistantSession s where s.sessionKey = :sessionKey
            )
            """)
    int deleteBySessionKey(@Param("sessionKey") UUID sessionKey);
}
