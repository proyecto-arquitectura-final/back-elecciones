package co.edu.elecciones;

import co.edu.elecciones.repository.AuditEventRepository;
import co.edu.elecciones.repository.CandidateRepository;
import co.edu.elecciones.repository.ElectionRepository;
import co.edu.elecciones.repository.OfficialResultRepository;
import co.edu.elecciones.repository.PartyRepository;
import co.edu.elecciones.repository.PollRepository;
import co.edu.elecciones.repository.PollResultRepository;
import co.edu.elecciones.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryQueryDeclarationTest {

    private static final List<Class<?>> REPOSITORIES = List.of(
            AuditEventRepository.class,
            CandidateRepository.class,
            ElectionRepository.class,
            OfficialResultRepository.class,
            PartyRepository.class,
            PollRepository.class,
            PollResultRepository.class,
            UserRepository.class
    );

    @Test
    void everyDeclaredRepositoryOperationUsesAnExplicitQueryStatement() {
        for (Class<?> repository : REPOSITORIES) {
            for (Method method : repository.getDeclaredMethods()) {
                assertTrue(
                        method.isAnnotationPresent(Query.class),
                        () -> repository.getSimpleName() + "." + method.getName()
                                + " debe declarar una sentencia explícita con @Query"
                );
            }
        }
    }
}
