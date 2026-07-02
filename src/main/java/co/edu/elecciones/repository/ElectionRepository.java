package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Election;
import co.edu.elecciones.domain.ElectionState;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ElectionRepository extends StatementRepository<Election> {

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

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Election e
            where e.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
