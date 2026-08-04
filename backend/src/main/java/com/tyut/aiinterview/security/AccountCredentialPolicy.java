package com.tyut.aiinterview.security;

/**
 * Shared account-credential constraints for public registration and administrator-created accounts.
 */
public final class AccountCredentialPolicy {
    private AccountCredentialPolicy() {}

    /** An English letter followed by English letters, numbers, or underscores; 4–32 characters. */
    public static final String USERNAME_REGEX = "^[A-Za-z][A-Za-z0-9_]{3,31}$";
    /** Printable ASCII only, 8–64 characters, containing at least one English letter and one digit. */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$";

    public static final String USERNAME_MESSAGE = "用户名须为 4-32 位英文开头的英文、数字或下划线，不能包含中文或空格";
    public static final String PASSWORD_MESSAGE = "密码须为 8-64 位英文、数字或半角符号，且至少包含一个字母和一个数字，不能包含中文或空格";
}
