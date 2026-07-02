package co.edu.elecciones.service;

import co.edu.elecciones.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserRepository repository;

    public UserDetailsServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var user = repository.selectByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(email));
        return new User(
                user.email,
                user.password,
                user.active,
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role.name()))
        );
    }
}
