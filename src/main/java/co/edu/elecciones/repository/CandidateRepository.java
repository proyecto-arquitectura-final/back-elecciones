package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Candidate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends StatementRepository<Candidate> {

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            order by c.name
            """)
    List<Candidate> selectAll();

    @Query("""
            select c
            from Candidate c
            join fetch c.party p
            where c.id = :id
            """)
    Optional<Candidate> selectById(@Param("id") Long id);

    @Query("""
            select count(c)
            from Candidate c
            """)
    long selectCount();

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Candidate c
            where c.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
