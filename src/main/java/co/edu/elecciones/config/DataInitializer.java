package co.edu.elecciones.config;

import co.edu.elecciones.domain.AppUser;
import co.edu.elecciones.domain.Role;
import co.edu.elecciones.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DataInitializer {

    @Value("${app.seed.admin-name}")
    private String adminName;
    @Value("${app.seed.admin-email}")
    private String adminEmail;
    @Value("${app.seed.admin-password}")
    private String adminPassword;
    @Value("${app.seed.analyst-name}")
    private String analystName;
    @Value("${app.seed.analyst-email}")
    private String analystEmail;
    @Value("${app.seed.analyst-password}")
    private String analystPassword;

    @Bean
    CommandLineRunner seed(UserRepository users, PasswordEncoder encoder) {
        return args -> initialize(users, encoder);
    }

    @Transactional
    void initialize(UserRepository users, PasswordEncoder encoder) {
        createUserIfMissing(users, encoder, adminName, adminEmail, adminPassword, Role.ADMINISTRADOR);
        createUserIfMissing(users, encoder, analystName, analystEmail, analystPassword, Role.ANALISTA);
    }

    private void createUserIfMissing(
            UserRepository users,
            PasswordEncoder encoder,
            String name,
            String email,
            String rawPassword,
            Role role
    ) {
        if (users.selectEmailCount(email) != 0) {
            return;
        }

        AppUser user = new AppUser();
        user.name = name;
        user.email = email;
        user.password = encoder.encode(rawPassword);
        user.role = role;
        users.save(user);
    }
}
