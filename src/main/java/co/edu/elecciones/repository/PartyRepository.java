package co.edu.elecciones.repository;

import co.edu.elecciones.domain.Party;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartyRepository extends StatementRepository<Party> {

    @Query("""
            select p
            from Party p
            order by p.name
            """)
    List<Party> selectAll();

    @Query("""
            select p
            from Party p
            where p.id = :id
            """)
    Optional<Party> selectById(@Param("id") Long id);

    @Query("""
            select count(p)
            from Party p
            """)
    long selectCount();

    @Query("""
            select count(p)
            from Party p
            where lower(p.acronym) = lower(:acronym)
            """)
    long selectAcronymCount(@Param("acronym") String acronym);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Party p
            where p.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
