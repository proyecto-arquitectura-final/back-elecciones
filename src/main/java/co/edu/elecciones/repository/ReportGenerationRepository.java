package co.edu.elecciones.repository;

import co.edu.elecciones.domain.ReportGeneration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReportGenerationRepository extends StatementRepository<ReportGeneration> {

    @Query("""
            select r
            from ReportGeneration r
            join fetch r.election e
            where e.id = :electionId
            order by r.generatedAt desc, r.id desc
            """)
    List<ReportGeneration> selectByElectionId(@Param("electionId") Long electionId);

    @Query(value = """
            select *
            from report_generation r
            where r.election_id = :electionId
              and r.format = :format
            order by r.generated_at desc, r.id desc
            limit 1
            """, nativeQuery = true)
    Optional<ReportGeneration> selectLatest(
            @Param("electionId") Long electionId,
            @Param("format") String format
    );
}

