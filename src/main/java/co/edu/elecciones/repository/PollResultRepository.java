package co.edu.elecciones.repository;

import co.edu.elecciones.domain.PollResult;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PollResultRepository extends StatementRepository<PollResult> {

    @Query("""
            select pr
            from PollResult pr
            join fetch pr.poll p
            join fetch pr.candidate c
            join fetch c.party party
            order by p.date desc, p.id desc, pr.id
            """)
    List<PollResult> selectAllWithDetails();

    @Query("""
            select pr
            from PollResult pr
            join fetch pr.candidate c
            join fetch c.party party
            where pr.poll.id = :pollId
            order by pr.id
            """)
    List<PollResult> selectByPollId(@Param("pollId") Long pollId);

    @Query("""
            select pr
            from PollResult pr
            join fetch pr.poll p
            join fetch pr.candidate c
            join fetch c.party party
            where p.id in :pollIds
            order by p.id, pr.id
            """)
    List<PollResult> selectByPollIds(@Param("pollIds") List<Long> pollIds);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from PollResult pr
            where pr.poll.id = :pollId
            """)
    int deleteByPollIdStatement(@Param("pollId") Long pollId);
}
