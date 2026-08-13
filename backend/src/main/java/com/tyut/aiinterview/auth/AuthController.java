package com.tyut.aiinterview.auth;

import com.tyut.aiinterview.common.ApiResponse;
import com.tyut.aiinterview.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetService passwordResetService;
    private final CurrentUser currentUser;
    public AuthController(AuthService authService, VerificationCodeService verificationCodeService,
                          PasswordResetService passwordResetService, CurrentUser currentUser) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
        this.passwordResetService = passwordResetService;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.UserProfile> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }
    @PostMapping("/register/code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody AuthDtos.SendVerificationCodeRequest request) {
        verificationCodeService.sendRegisterCode(request);
        return ApiResponse.ok();
    }
    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }
    @PostMapping("/login/code/send")
    public ApiResponse<Void> sendLoginCode(@Valid @RequestBody AuthDtos.SendLoginCodeRequest request) {
        authService.sendLoginCode(request);
        return ApiResponse.ok();
    }
    @PostMapping("/login/code")
    public ApiResponse<AuthDtos.LoginResponse> loginWithCode(@Valid @RequestBody AuthDtos.CodeLoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.loginWithCode(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }
    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.LoginResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.refresh(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authService.logout(request, currentUser.id());
        return ApiResponse.ok();
    }
    @PostMapping("/password/reset/code")
    public ApiResponse<AuthDtos.PasswordResetCodeResponse> sendPasswordResetCode(
            @Valid @RequestBody AuthDtos.PasswordResetCodeRequest request) {
        return ApiResponse.ok(passwordResetService.sendCode(request));
    }
    @PostMapping("/password/reset")
    public ApiResponse<AuthDtos.PasswordResetResponse> resetPassword(
            @Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        return ApiResponse.ok(passwordResetService.reset(request));
    }
    @GetMapping("/me")
    public ApiResponse<AuthDtos.UserProfile> me() {
        return ApiResponse.ok(authService.profileOf(currentUser.id()));
    }
}
