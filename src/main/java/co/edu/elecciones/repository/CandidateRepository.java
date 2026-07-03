package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Candidate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends StatementRepository<Candidate> {

    interface ManagementRow {
        Long getId();
        Instant getCreatedAt();
        Instant getUpdatedAt();
        String getName();
        String getVicePresidentName();
        String getDepartment();
        String getMunicipality();
        Boolean getActive();
        String getElectionType();
        Long getPartyId();
        String getPartyName();
        String getPartyAcronym();
        String getPartyColor();
        Boolean getPartyActive();
        Long getElectionId();
        String getElectionName();
        String getElectionRound();
        LocalDate getElectionDate();
        String getElectionState();
        Long getOfficialResultCount();
        Long getPollResultCount();
    }

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            join fetch c.election e
            order by c.name
            """)
    List<Candidate> selectAll();

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            join fetch c.election e
            where c.id = :id
            """)
    Optional<Candidate> selectById(@Param("id") Long id);

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            join fetch c.election e
            where c.id in :ids
            order by c.id
            """)
    List<Candidate> selectByIds(@Param("ids") Collection<Long> ids);

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            join fetch c.election e
            where e.id = :electionId
            order by c.active desc, c.name, c.id
            """)
    List<Candidate> selectByElectionId(@Param("electionId") Long electionId);

    @Query(value = """
            select c.id as "id",
                   c.created_at as "createdAt",
                   c.updated_at as "updatedAt",
                   c.name as "name",
                   c.vice_president_name as "vicePresidentName",
                   c.department as "department",
                   c.municipality as "municipality",
                   c.active as "active",
                   c.election_type as "electionType",
                   p.id as "partyId",
                   p.name as "partyName",
                   p.acronym as "partyAcronym",
                   p.color as "partyColor",
                   p.active as "partyActive",
                   e.id as "electionId",
                   e.name as "electionName",
                   e.round as "electionRound",
                   e.election_date as "electionDate",
                   e.state as "electionState",
                   (select count(*) from official_result r where r.candidate_id = c.id) as "officialResultCount",
                   (select count(*) from poll_result pr where pr.candidate_id = c.id) as "pollResultCount"
            from candidate c
            join party p on p.id = c.party_id
            join election e on e.id = c.election_id
            order by c.active desc, c.name asc, c.id asc
            """, nativeQuery = true)
    List<ManagementRow> selectManagementRows();

    @Query("""
            select count(c)
            from Candidate c
            """)
    long selectCount();

    @Query("""
            select count(c)
            from Candidate c
            where c.election.id = :electionId
              and lower(trim(c.name)) = lower(trim(:name))
              and (:excludeId is null or c.id <> :excludeId)
            """)
    long selectDuplicateNameCount(
            @Param("electionId") Long electionId,
            @Param("name") String name,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            select count(r)
            from OfficialResult r
            where r.candidate.id = :candidateId
            """)
    long selectOfficialResultCount(@Param("candidateId") Long candidateId);

    @Query("""
            select count(pr)
            from PollResult pr
            where pr.candidate.id = :candidateId
            """)
    long selectPollResultCount(@Param("candidateId") Long candidateId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from Candidate c
            where c.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
