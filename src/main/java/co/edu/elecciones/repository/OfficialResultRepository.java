package co.edu.elecciones.repository;

import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.ResultValidationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OfficialResultRepository extends StatementRepository<OfficialResult> {

    interface CandidateVoteAggregate {
        String getCandidate();
        String getParty();
        Long getVotes();
    }

    interface ManagementAggregate {
        Long getRecords();
        Long getCandidateVotes();
        Long getValidated();
        Long getPending();
        Long getRejected();
        Instant getLastImportedAt();
    }

    interface TerritoryTables {
        String getDepartment();
        String getMunicipality();
        Integer getReportedTables();
        Integer getTotalTables();
        Double getParticipation();
    }

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            order by e.electionDate desc, r.importedAt desc, r.id desc
            """)
    List<OfficialResult> selectAll();

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where r.id = :id
            """)
    Optional<OfficialResult> selectById(@Param("id") Long id);

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where e.id = :electionId
            order by r.votes desc, r.id
            """)
    List<OfficialResult> selectByElectionId(@Param("electionId") Long electionId);

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where lower(r.department) = lower(:department)
            order by r.votes desc, r.id
            """)
    List<OfficialResult> selectByDepartment(@Param("department") String department);

    @Query(value = """
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where e.id = :electionId
              and (:status is null or r.validationStatus = :status)
              and (:department = '' or lower(coalesce(r.department, '')) = lower(:department))
              and (:municipality = '' or lower(coalesce(r.municipality, '')) = lower(:municipality))
              and (
                    :search = ''
                    or lower(c.name) like lower(concat('%', :search, '%'))
                    or lower(p.name) like lower(concat('%', :search, '%'))
                    or lower(p.acronym) like lower(concat('%', :search, '%'))
                    or lower(coalesce(r.department, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(r.municipality, '')) like lower(concat('%', :search, '%'))
                    or lower(r.source) like lower(concat('%', :search, '%'))
              )
            order by r.importedAt desc, r.id desc
            """,
            countQuery = """
            select count(r)
            from OfficialResult r
            join r.election e
            join r.candidate c
            join c.party p
            where e.id = :electionId
              and (:status is null or r.validationStatus = :status)
              and (:department = '' or lower(coalesce(r.department, '')) = lower(:department))
              and (:municipality = '' or lower(coalesce(r.municipality, '')) = lower(:municipality))
              and (
                    :search = ''
                    or lower(c.name) like lower(concat('%', :search, '%'))
                    or lower(p.name) like lower(concat('%', :search, '%'))
                    or lower(p.acronym) like lower(concat('%', :search, '%'))
                    or lower(coalesce(r.department, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(r.municipality, '')) like lower(concat('%', :search, '%'))
                    or lower(r.source) like lower(concat('%', :search, '%'))
              )
            """)
    Page<OfficialResult> selectPage(
            @Param("electionId") Long electionId,
            @Param("status") ResultValidationStatus status,
            @Param("department") String department,
            @Param("municipality") String municipality,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where e.id = :electionId
              and c.id = :candidateId
              and coalesce(lower(r.department), '') = coalesce(lower(:department), '')
              and coalesce(lower(r.municipality), '') = coalesce(lower(:municipality), '')
            """)
    Optional<OfficialResult> selectByNaturalKey(
            @Param("electionId") Long electionId,
            @Param("candidateId") Long candidateId,
            @Param("department") String department,
            @Param("municipality") String municipality
    );

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where e.id = :electionId
              and coalesce(lower(r.department), '') = coalesce(lower(:department), '')
              and coalesce(lower(r.municipality), '') = coalesce(lower(:municipality), '')
            order by r.id
            """)
    List<OfficialResult> selectByScope(
            @Param("electionId") Long electionId,
            @Param("department") String department,
            @Param("municipality") String municipality
    );

    @Query("""
            select count(r) as records,
                   coalesce(sum(case when r.validationStatus = :validatedStatus then r.votes else 0 end), 0) as candidateVotes,
                   coalesce(sum(case when r.validationStatus = :validatedStatus then 1 else 0 end), 0) as validated,
                   coalesce(sum(case when r.validationStatus = :pendingStatus then 1 else 0 end), 0) as pending,
                   coalesce(sum(case when r.validationStatus = :rejectedStatus then 1 else 0 end), 0) as rejected,
                   max(r.importedAt) as lastImportedAt
            from OfficialResult r
            where r.election.id = :electionId
            """)
    ManagementAggregate selectManagementAggregate(
            @Param("electionId") Long electionId,
            @Param("validatedStatus") ResultValidationStatus validatedStatus,
            @Param("pendingStatus") ResultValidationStatus pendingStatus,
            @Param("rejectedStatus") ResultValidationStatus rejectedStatus
    );

    @Query("""
            select coalesce(r.department, '') as department,
                   coalesce(r.municipality, '') as municipality,
                   max(r.reportedTables) as reportedTables,
                   max(r.totalTables) as totalTables,
                   max(r.participation) as participation
            from OfficialResult r
            where r.election.id = :electionId
            group by coalesce(r.department, ''), coalesce(r.municipality, '')
            order by coalesce(r.department, ''), coalesce(r.municipality, '')
            """)
    List<TerritoryTables> selectTerritoryTables(@Param("electionId") Long electionId);

    @Query("""
            select distinct r.department
            from OfficialResult r
            where r.election.id = :electionId
              and r.department is not null
              and trim(r.department) <> ''
            order by r.department
            """)
    List<String> selectDepartments(@Param("electionId") Long electionId);

    @Query("""
            select distinct r.municipality
            from OfficialResult r
            where r.election.id = :electionId
              and (:department = '' or lower(coalesce(r.department, '')) = lower(:department))
              and r.municipality is not null
              and trim(r.municipality) <> ''
            order by r.municipality
            """)
    List<String> selectMunicipalities(
            @Param("electionId") Long electionId,
            @Param("department") String department
    );

    @Query("""
            select count(r)
            from OfficialResult r
            """)
    long selectCount();

    @Query("""
            select coalesce(sum(r.votes), 0)
            from OfficialResult r
            """)
    Long selectTotalVotes();

    @Query("""
            select coalesce(avg(r.participation), 0.0)
            from OfficialResult r
            """)
    Double selectAverageParticipation();

    @Query("""
            select coalesce(avg(
                case
                    when r.totalTables is null or r.totalTables = 0 then 0.0
                    else (r.reportedTables * 100.0 / r.totalTables)
                end
            ), 0.0)
            from OfficialResult r
            """)
    Double selectAverageReportedTablePercentage();

    @Query("""
            select c.name as candidate,
                   p.name as party,
                   coalesce(sum(r.votes), 0) as votes
            from OfficialResult r
            join r.candidate c
            join c.party p
            group by c.name, p.name
            order by sum(r.votes) desc
            """)
    List<CandidateVoteAggregate> selectVotesGroupedByCandidate();

    @Query("""
            select c.name as candidate,
                   p.name as party,
                   coalesce(sum(r.votes), 0) as votes
            from OfficialResult r
            join r.candidate c
            join c.party p
            where r.election.id = :electionId
            group by c.name, p.name
            order by sum(r.votes) desc
            """)
    List<CandidateVoteAggregate> selectVotesGroupedByCandidateForElection(@Param("electionId") Long electionId);

    interface ReportRegionRow {
        String getRegion();
        Long getVotes();
        Double getParticipation();
        Long getReportedTables();
        Long getTotalTables();
    }

    @Query(value = """
            with region_votes as (
                select coalesce(nullif(btrim(r.department), ''), 'Sin región') as region,
                       sum(r.votes) as votes
                from official_result r
                where r.election_id = :electionId
                  and r.validation_status = 'VALIDADO'
                group by coalesce(nullif(btrim(r.department), ''), 'Sin región')
            ), territory as (
                select coalesce(nullif(btrim(r.department), ''), 'Sin región') as region,
                       coalesce(nullif(btrim(r.municipality), ''), 'Sin municipio') as municipality,
                       max(r.reported_tables) as reported_tables,
                       max(r.total_tables) as total_tables,
                       max(r.participation) as participation
                from official_result r
                where r.election_id = :electionId
                  and r.validation_status = 'VALIDADO'
                group by coalesce(nullif(btrim(r.department), ''), 'Sin región'),
                         coalesce(nullif(btrim(r.municipality), ''), 'Sin municipio')
            ), region_territory as (
                select t.region,
                       sum(t.reported_tables) as reported_tables,
                       sum(t.total_tables) as total_tables,
                       case when sum(greatest(t.total_tables, 1)) = 0 then 0
                            else sum(t.participation * greatest(t.total_tables, 1)) / sum(greatest(t.total_tables, 1))
                       end as participation
                from territory t
                group by t.region
            )
            select v.region as "region",
                   v.votes as "votes",
                   coalesce(t.participation, 0) as "participation",
                   coalesce(t.reported_tables, 0) as "reportedTables",
                   coalesce(t.total_tables, 0) as "totalTables"
            from region_votes v
            left join region_territory t on t.region = v.region
            order by v.votes desc, v.region
            """, nativeQuery = true)
    List<ReportRegionRow> selectReportRegions(@Param("electionId") Long electionId);

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            where e.id = :electionId
              and r.validationStatus = co.edu.elecciones.domain.ResultValidationStatus.VALIDADO
            order by coalesce(r.department, ''), coalesce(r.municipality, ''), r.votes desc, r.id
            """)
    List<OfficialResult> selectValidatedByElectionId(@Param("electionId") Long electionId);

    @Query("""
            select max(r.election.id)
            from OfficialResult r
            """)
    Long selectLatestElectionIdWithResults();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from OfficialResult r
            where r.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
