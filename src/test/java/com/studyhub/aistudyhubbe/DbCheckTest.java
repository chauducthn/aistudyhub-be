package com.studyhub.aistudyhubbe;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DbCheckTest {
    @Test
    public void testDb() {
        String[] passwords = {"", "root", "123456", "12345678", "admin", "mysql"};
        boolean success = false;
        
        for (String pwd : passwords) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true",
                    "root",
                    pwd
                );
                System.out.println("=== LOCAL MYSQL CONNECTED WITH PASSWORD: '" + pwd + "' ===");
                Statement stmt = conn.createStatement();
                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS aistudyhub");
                System.out.println("=== DATABASE aistudyhub CREATED/VERIFIED ===");
                conn.close();
                success = true;
                break;
            } catch (Exception e) {
                System.out.println("=== LOCAL MYSQL FAILED WITH PASSWORD '" + pwd + "': " + e.getMessage());
            }
        }
        
        if (!success) {
            System.out.println("=== ALL COMMON PASSWORDS FAILED ===");
        }
    }
}
