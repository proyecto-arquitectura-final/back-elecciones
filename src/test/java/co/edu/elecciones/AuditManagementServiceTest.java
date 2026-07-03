package co.edu.elecciones;

import co.edu.elecciones.domain.AuditEvent;
import co.edu.elecciones.repository.AuditEventRepository;
import co.edu.elecciones.service.AuditManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditManagementServiceTest {
    @Mock AuditEventRepository repository;

    @Test
    void returnsPersistedCountersOptionsAndPage() {
        AuditEvent event = new AuditEvent();
        event.id = 7L;
        event.at = Instant.parse("2026-07-03T00:00:00Z");
        event.username = "admin@test.co";
        event.action = "CREATE";
        event.entity = "AppUser";
        event.details = "Creación";
        event.ip = "127.0.0.1";
        event.success = true;
        when(repository.selectPage(eq("admin"), eq("CREATE"), eq("AppUser"), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        AuditEventRepository.AuditAggregate aggregate = mock(AuditEventRepository.AuditAggregate.class);
        when(aggregate.getTotal()).thenReturn(3L);
        when(aggregate.getSuccessful()).thenReturn(2L);
        when(aggregate.getFailed()).thenReturn(1L);
        when(aggregate.getUsers()).thenReturn(2L);
        when(repository.selectAggregate()).thenReturn(aggregate);
        when(repository.selectActions()).thenReturn(List.of("CREATE"));
        when(repository.selectEntities()).thenReturn(List.of("AppUser"));

        var response = new AuditManagementService(repository)
                .getManagement(" admin ", "CREATE", "AppUser", true, 0, 20);

        assertEquals(3L, response.counters().total());
        assertEquals(1, response.events().items().size());
        assertEquals("admin@test.co", response.events().items().get(0).username());
        assertEquals(List.of("CREATE"), response.actions());
    }
}
