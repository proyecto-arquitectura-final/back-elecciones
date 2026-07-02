package co.edu.elecciones.repository;

import co.edu.elecciones.domain.AuditEvent;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditEventRepository extends StatementRepository<AuditEvent> {

    @Query("""
            select a
            from AuditEvent a
            order by a.at desc, a.id desc
            """)
    List<AuditEvent> selectAll();

    @Query("""
            select count(a)
            from AuditEvent a
            """)
    long selectCount();
}
