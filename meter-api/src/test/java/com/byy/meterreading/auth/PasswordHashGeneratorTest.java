package com.byy.meterreading.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Scanner;

/**
 * 生成初始化用户所需的 BCrypt 密码哈希。
 */
class PasswordHashGeneratorTest {

    public static void main(String[] args) {
        // 在本地控制台输入明文密码，不将密码写入源码。
        System.out.print("请输入需要生成哈希的密码：");
        Scanner scanner = new Scanner(System.in);
        String plainPassword = scanner.nextLine();
        if (plainPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        // 使用与登录认证相同的 BCrypt 算法生成密码哈希。
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String passwordHash = passwordEncoder.encode(plainPassword);

        // 输出前验证哈希确实能够匹配原始密码。
        if (!passwordEncoder.matches(plainPassword, passwordHash)) {
            throw new IllegalStateException("生成的密码哈希校验失败");
        }
        System.out.println("BCrypt password hash: " + passwordHash);
    }
}
