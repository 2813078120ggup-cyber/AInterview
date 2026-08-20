package com.tyut.aiinterview.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AuthDtosValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void protectedLoginAndCodeSendRequestsRequireImageCaptchaFields() {
        assertTrue(validator.validate(new AuthDtos.LoginRequest("candidate", "Password123"))
                .stream().anyMatch(error -> error.getPropertyPath().toString().equals("captchaChallengeId")));
        assertTrue(validator.validate(new AuthDtos.SendLoginCodeRequest("sms", "13800138000"))
                .stream().anyMatch(error -> error.getPropertyPath().toString().equals("captchaChallengeId")));
        assertTrue(validator.validate(new AuthDtos.SendVerificationCodeRequest("13800138000", ""))
                .stream().anyMatch(error -> error.getPropertyPath().toString().equals("captchaChallengeId")));
        assertTrue(validator.validate(new AuthDtos.PasswordResetCodeRequest("sms", "13800138000"))
                .stream().anyMatch(error -> error.getPropertyPath().toString().equals("captchaChallengeId")));
    }
}
