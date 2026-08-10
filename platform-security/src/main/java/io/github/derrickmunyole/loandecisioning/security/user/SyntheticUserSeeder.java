package io.github.derrickmunyole.loandecisioning.security.user;

import io.github.derrickmunyole.loandecisioning.security.Role;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seeds one synthetic user per {@link Role} so every actor can log in against a fresh stack. */
@Component
public class SyntheticUserSeeder implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String seedPassword;

    public SyntheticUserSeeder(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.seed-users-password}") String seedPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedPassword = seedPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Role role : Role.values()) {
            String username = role.name().toLowerCase(Locale.ROOT);
            if (!appUserRepository.existsByUsername(username)) {
                appUserRepository.save(
                        new AppUser(username, passwordEncoder.encode(seedPassword), role));
            }
        }
    }
}
