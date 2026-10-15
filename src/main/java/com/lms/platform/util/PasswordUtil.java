package com.lms.platform.util;
import at.favre.lib.crypto.bcrypt.BCrypt;
public class PasswordUtil {
    public static String hash(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }
    public static boolean verify(String password, String hash) {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
    }
}
