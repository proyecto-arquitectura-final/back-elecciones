package co.edu.elecciones;

import co.edu.elecciones.config.BusinessConflictException;
import co.edu.elecciones.domain.AppUser;
import co.edu.elecciones.domain.Role;
import co.edu.elecciones.dto.Requests.UserRequest;
import co.edu.elecciones.repository.UserRepository;
import co.edu.elecciones.service.AuditService;
import co.edu.elecciones.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {
    @Mock UserRepository repository;
    @Mock AuditService audit;
    @Mock PasswordEncoder encoder;
    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(repository, audit, encoder);
    }

    @Test
    void buildsManagementWithoutExposingPassword() {
        AppUser user = user(Role.ADMINISTRADOR, true);
        when(repository.selectPage(eq(""), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));
        UserRepository.UserAggregate aggregate = mock(UserRepository.UserAggregate.class);
        when(aggregate.getTotal()).thenReturn(1L);
        when(aggregate.getActive()).thenReturn(1L);
        when(aggregate.getAdministrators()).thenReturn(1L);
        when(aggregate.getAnalysts()).thenReturn(0L);
        when(repository.selectAggregate()).thenReturn(aggregate);

        var response = service.getManagement("", 0, 10);

        assertEquals("admin@test.co", response.users().items().get(0).email());
        assertEquals("ADMINISTRADOR", response.users().items().get(0).role());
    }

    @Test
    void rejectsNormalizedDuplicateEmail() {
        when(repository.selectEmailCount("admin@test.co", null)).thenReturn(1L);
        UserRequest request = new UserRequest("Admin", " ADMIN@TEST.CO ", "12345678", Role.ADMINISTRADOR, true);

        assertThrows(BusinessConflictException.class, () -> service.create(request, null));
        verify(repository, never()).save(any());
    }

    @Test
    void preventsDeletingLastActiveAdministrator() {
        AppUser user = user(Role.ADMINISTRADOR, true);
        when(repository.selectById(1L)).thenReturn(Optional.of(user));
        when(repository.selectActiveCountByRole(Role.ADMINISTRADOR)).thenReturn(1L);

        assertThrows(BusinessConflictException.class, () -> service.delete(1L, null));
        verify(repository, never()).deleteByIdStatement(1L);
    }

    private AppUser user(Role role, boolean active) {
        AppUser user = new AppUser();
        user.id = 1L;
        user.name = "Admin";
        user.email = "admin@test.co";
        user.password = "secret";
        user.role = role;
        user.active = active;
        return user;
    }
}
