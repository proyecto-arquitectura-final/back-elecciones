package co.edu.elecciones.repository;

import co.edu.elecciones.domain.ElectionResultSummary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElectionResultSummaryRepository extends StatementRepository<ElectionResultSummary> {

    @Query("""
            select s
            from ElectionResultSummary s
            join fetch s.election e
            where e.id = :electionId
            """)
    Optional<ElectionResultSummary> selectByElectionId(@Param("electionId") Long electionId);

    @Query("""
            select s
            from ElectionResultSummary s
            join fetch s.election e
            order by e.electionDate desc, e.id desc
            """)
    List<ElectionResultSummary> selectAll();

    @Query("""
            select count(s)
            from ElectionResultSummary s
            """)
    long selectCount();
}
