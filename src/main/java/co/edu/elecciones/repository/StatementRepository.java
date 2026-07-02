package co.edu.elecciones.repository;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Repositorio base limitado a operaciones de escritura.
 * Las lecturas, conteos y eliminaciones se declaran expresamente mediante @Query
 * en cada repositorio concreto; así se evitan consultas derivadas por nombre.
 */
@NoRepositoryBean
public interface StatementRepository<T> extends Repository<T, Long> {
    <S extends T> S save(S entity);

    <S extends T> List<S> saveAll(Iterable<S> entities);
}
