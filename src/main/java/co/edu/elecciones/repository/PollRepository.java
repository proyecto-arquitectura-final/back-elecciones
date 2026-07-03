package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Poll;
import co.edu.elecciones.domain.PollStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends StatementRepository<Poll> {

    interface CountersRow {
        Long getTotal();
        Long getApproved();
        Long getPending();
        Long getRejected();
        Double getAverageSample();
    }

    @Query("""
            select p
            from Poll p
            join fetch p.election e
            order by p.date desc, p.id desc
            """)
    List<Poll> selectAll();

    @Query("""
            select p
            from Poll p
            join fetch p.election e
            where p.id = :id
            """)
    Optional<Poll> selectById(@Param("id") Long id);

    @Query(value = """
            select p
            from Poll p
            join fetch p.election e
            where (:electionId is null or e.id = :electionId)
              and (:status is null or p.status = :status)
              and (
                    :search = ''
                    or lower(p.source) like lower(concat('%', :search, '%'))
                    or lower(p.methodology) like lower(concat('%', :search, '%'))
                    or lower(e.name) like lower(concat('%', :search, '%'))
              )
            order by p.date desc, p.id desc
            """,
            countQuery = """
            select count(p)
            from Poll p
            join p.election e
            where (:electionId is null or e.id = :electionId)
              and (:status is null or p.status = :status)
              and (
                    :search = ''
                    or lower(p.source) like lower(concat('%', :search, '%'))
                    or lower(p.methodology) like lower(concat('%', :search, '%'))
                    or lower(e.name) like lower(concat('%', :search, '%'))
              )
            """)
    Page<Poll> selectPage(
            @Param("electionId") Long electionId,
            @Param("status") PollStatus status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query(value = """
            select count(*) as "total",
                   count(*) filter (where p.status = 'APROBADA') as "approved",
                   count(*) filter (where p.status = 'PENDIENTE') as "pending",
                   count(*) filter (where p.status = 'RECHAZADA') as "rejected",
                   coalesce(avg(p.sample_size), 0) as "averageSample"
            from poll p
            where (:electionId is null or p.election_id = :electionId)
            """, nativeQuery = true)
    CountersRow selectCounters(@Param("electionId") Long electionId);

    @Query("""
            select count(p)
            from Poll p
            """)
    long selectCount();

    @Query("""
            select count(p)
            from Poll p
            where p.election.id = :electionId
              and lower(trim(p.source)) = lower(trim(:source))
              and p.date = :date
              and (:excludeId is null or p.id <> :excludeId)
            """)
    long selectDuplicateCount(
            @Param("electionId") Long electionId,
            @Param("source") String source,
            @Param("date") java.time.LocalDate date,
            @Param("excludeId") Long excludeId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Poll p
            where p.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
