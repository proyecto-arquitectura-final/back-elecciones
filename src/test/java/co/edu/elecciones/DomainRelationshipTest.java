package co.edu.elecciones;

import co.edu.elecciones.domain.Candidate;
import co.edu.elecciones.domain.OfficialResult;
import co.edu.elecciones.domain.PollResult;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainRelationshipTest {

    private static final List<Class<?>> DOMAIN_TYPES = List.of(
            co.edu.elecciones.domain.AppUser.class,
            co.edu.elecciones.domain.AssistantSession.class,
            co.edu.elecciones.domain.AssistantMessage.class,
            co.edu.elecciones.domain.AuditEvent.class,
            Candidate.class,
            co.edu.elecciones.domain.Election.class,
            co.edu.elecciones.domain.ElectionResultSummary.class,
            OfficialResult.class,
            co.edu.elecciones.domain.Party.class,
            co.edu.elecciones.domain.Poll.class,
            PollResult.class
    );

    @Test
    void domainDoesNotUseOneToManyOrManyToMany() {
        for (Class<?> type : DOMAIN_TYPES) {
            for (Field field : type.getDeclaredFields()) {
                assertFalse(
                        field.isAnnotationPresent(OneToMany.class),
                        () -> type.getSimpleName() + "." + field.getName() + " no debe usar @OneToMany"
                );
                assertFalse(
                        field.isAnnotationPresent(ManyToMany.class),
                        () -> type.getSimpleName() + "." + field.getName() + " no debe usar @ManyToMany"
                );
            }
        }
    }

    @Test
    void requiredRelationsRemainManyToOne() throws Exception {
        assertTrue(Candidate.class.getDeclaredField("party").isAnnotationPresent(ManyToOne.class));
        assertTrue(co.edu.elecciones.domain.AssistantSession.class.getDeclaredField("election").isAnnotationPresent(ManyToOne.class));
        assertTrue(co.edu.elecciones.domain.AssistantMessage.class.getDeclaredField("session").isAnnotationPresent(ManyToOne.class));
        assertTrue(co.edu.elecciones.domain.ElectionResultSummary.class.getDeclaredField("election").isAnnotationPresent(ManyToOne.class));
        assertTrue(OfficialResult.class.getDeclaredField("election").isAnnotationPresent(ManyToOne.class));
        assertTrue(OfficialResult.class.getDeclaredField("candidate").isAnnotationPresent(ManyToOne.class));
        assertTrue(PollResult.class.getDeclaredField("poll").isAnnotationPresent(ManyToOne.class));
        assertTrue(PollResult.class.getDeclaredField("candidate").isAnnotationPresent(ManyToOne.class));
    }
}
