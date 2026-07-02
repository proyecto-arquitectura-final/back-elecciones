package co.edu.elecciones.repository;

import co.edu.elecciones.domain.OfficialResult;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfficialResultRepository extends StatementRepository<OfficialResult> {

    interface CandidateVoteAggregate {
        String getCandidate();
        String getParty();
        Long getVotes();
    }

    @Query("""
            select r
            from OfficialResult r
            join fetch r.election e
            join fetch r.candidate c
            join fetch c.party p
            order by e.electionDate desc, r.votes desc, r.id
            """)
    List<OfficialResult> selectAll();

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
    java.util.Optional<OfficialResult> selectByNaturalKey(
            @Param("electionId") Long electionId,
            @Param("candidateId") Long candidateId,
            @Param("department") String department,
            @Param("municipality") String municipality
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
}
