package com.livestream.auth;

import com.livestream.user.User;
import com.livestream.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;

    public AuthController(AuthService auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    public record SignupRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$", message = "영문·숫자·_ 3~30자") String username,
            @NotBlank @Size(min = 8, max = 72, message = "8~72자") String password) {} // 72 = BCrypt 입력 한도

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UserResponse(Long id, String username) {
        static UserResponse of(User u) { return new UserResponse(u.getId(), u.getUsername()); }
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest req) {
        return UserResponse.of(auth.signup(req.username(), req.password()));
    }

    @PostMapping("/login")
    public AuthService.Tokens login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    @PostMapping("/refresh")
    public AuthService.Tokens refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        return users.findById(userId).map(UserResponse::of)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)); // 토큰은 유효하나 탈퇴 등으로 사용자 없음
    }
}
