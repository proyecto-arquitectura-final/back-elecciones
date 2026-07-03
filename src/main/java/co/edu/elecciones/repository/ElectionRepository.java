package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionRound;
import co.edu.elecciones.domain.ElectionState;
import co.edu.elecciones.domain.ElectionType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ElectionRepository extends StatementRepository<Election> {

    interface DashboardElectionRow {
        Long getId();
        String getName();
        ElectionType getType();
        LocalDate getElectionDate();
        ElectionState getState();
        Integer getReportedTables();
        Integer getTotalTables();
        Boolean getSummaryAvailable();
    }

    interface ManagementRow {
        Long getId();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        String getName();
        String getType();
        String getRound();
        LocalDate getElectionDate();
        String getState();
        Integer getReportedTables();
        Integer getTotalTables();
        Boolean getSummaryAvailable();
        Long getCandidateCount();
        Long getOfficialResultCount();
        Long getAssistantSessionCount();
    }

    @Query("""
            select e
            from Election e
            order by e.electionDate desc, e.id desc
            """)
    List<Election> selectAll();

    @Query("""
            select e
            from Election e
            where e.id = :id
            """)
    Optional<Election> selectById(@Param("id") Long id);

    @Query("""
            select count(e)
            from Election e
            """)
    long selectCount();

    @Query("""
            select count(e)
            from Election e
            where e.state in :states
            """)
    long selectActiveCount(@Param("states") Collection<ElectionState> states);

    @Query("""
            select count(e)
            from Election e
            where lower(trim(e.name)) = lower(trim(:name))
              and e.type = :type
              and e.round = :round
              and e.electionDate = :electionDate
              and (:excludeId is null or e.id <> :excludeId)
            """)
    long selectDuplicateDefinitionCount(
            @Param("name") String name,
            @Param("type") ElectionType type,
            @Param("round") ElectionRound round,
            @Param("electionDate") LocalDate electionDate,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            select e.id as id,
                   e.name as name,
                   e.type as type,
                   e.electionDate as electionDate,
                   e.state as state,
                   coalesce(s.reportedTables, 0) as reportedTables,
                   coalesce(s.totalTables, 0) as totalTables,
                   case when s.id is null then false else true end as summaryAvailable
            from Election e
            left join ElectionResultSummary s on s.election = e
            where e.state in :states
            order by e.electionDate asc, e.id asc
            """)
    List<DashboardElectionRow> selectDashboardElections(
            @Param("states") Collection<ElectionState> states
    );

    @Query(value = """
            select e.id as "id",
                   e.created_at as "createdAt",
                   e.updated_at as "updatedAt",
                   e.name as "name",
                   e.type as "type",
                   e.round as "round",
                   e.election_date as "electionDate",
                   e.state as "state",
                   coalesce(s.reported_tables, 0) as "reportedTables",
                   coalesce(s.total_tables, 0) as "totalTables",
                   case when s.id is null then false else true end as "summaryAvailable",
                   (select count(*) from candidate c where c.election_id = e.id) as "candidateCount",
                   (select count(*) from official_result r where r.election_id = e.id) as "officialResultCount",
                   (select count(*) from assistant_session a where a.election_id = e.id) as "assistantSessionCount"
            from election e
            left join election_result_summary s on s.election_id = e.id
            order by e.election_date desc, e.id desc
            """, nativeQuery = true)
    List<ManagementRow> selectManagementRows();

    @Query(value = """
            select e.id as "id",
                   e.created_at as "createdAt",
                   e.updated_at as "updatedAt",
                   e.name as "name",
                   e.type as "type",
                   e.round as "round",
                   e.election_date as "electionDate",
                   e.state as "state",
                   coalesce(s.reported_tables, 0) as "reportedTables",
                   coalesce(s.total_tables, 0) as "totalTables",
                   case when s.id is null then false else true end as "summaryAvailable",
                   (select count(*) from candidate c where c.election_id = e.id) as "candidateCount",
                   (select count(*) from official_result r where r.election_id = e.id) as "officialResultCount",
                   (select count(*) from assistant_session a where a.election_id = e.id) as "assistantSessionCount"
            from election e
            left join election_result_summary s on s.election_id = e.id
            where e.id = :id
            """, nativeQuery = true)
    Optional<ManagementRow> selectManagementRow(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Election e
            where e.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
