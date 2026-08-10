package io.github.derrickmunyole.loandecisioning.security.auth;

import io.github.derrickmunyole.loandecisioning.security.jwt.JwtService;
import io.github.derrickmunyole.loandecisioning.security.user.AppUser;
import io.github.derrickmunyole.loandecisioning.security.user.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            AppUserRepository appUserRepository,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser appUser = appUserRepository.findByUsername(request.username()).orElseThrow();
        String token = jwtService.issueToken(appUser.getUsername(), appUser.getRole().name());
        return new LoginResponse(token);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAuthenticationException() {}
}
