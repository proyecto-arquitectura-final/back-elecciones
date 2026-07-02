package co.edu.elecciones.repository;

import co.edu.elecciones.domain.AppUser;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends StatementRepository<AppUser> {

    @Query("""
            select u
            from AppUser u
            order by u.id
            """)
    List<AppUser> selectAll();

    @Query("""
            select u
            from AppUser u
            where u.id = :id
            """)
    Optional<AppUser> selectById(@Param("id") Long id);

    @Query("""
            select u
            from AppUser u
            where lower(u.email) = lower(:email)
            """)
    Optional<AppUser> selectByEmail(@Param("email") String email);

    @Query("""
            select count(u)
            from AppUser u
            """)
    long selectCount();

    @Query("""
            select count(u)
            from AppUser u
            where lower(u.email) = lower(:email)
            """)
    long selectEmailCount(@Param("email") String email);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AppUser u
            where u.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);
}
