package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Poll;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PollRepository extends StatementRepository<Poll> {

    @Query("""
            select p
            from Poll p
            order by p.date desc, p.id desc
            """)
    List<Poll> selectAll();

    @Query("""
            select p
            from Poll p
            where p.id = :id
            """)
    Optional<Poll> selectById(@Param("id") Long id);

    @Query("""
            select count(p)
            from Poll p
            """)
    long selectCount();

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Poll p
            where p.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
