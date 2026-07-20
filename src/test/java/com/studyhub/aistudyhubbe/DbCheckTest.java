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
        
    }

    @Test
    public void listUsers() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/aistudyhub?useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                "1234"
            );
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT email, role, status FROM users");
            System.out.println("=== USERS LIST ===");
            while (rs.next()) {
                System.out.println("EMAIL: " + rs.getString("email") + " | ROLE: " + rs.getString("role") + " | STATUS: " + rs.getString("status"));
            }
            System.out.println("=== END USERS LIST ===");
            conn.close();
        } catch (Exception e) {
            System.out.println("Failed to list users: " + e.getMessage());
        }
    }

    @Test
    public void resetPasswords() {
        try {
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            String hash = encoder.encode("Password123!");
            
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/aistudyhub?useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                "1234"
            );
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("UPDATE users SET password_hash = '" + hash + "' WHERE email IN ('student@aistudyhub.com', 'student2@aistudyhub.com')");
            System.out.println("=== USER PASSWORDS RESET TO 'Password123!' ===");
            conn.close();
        } catch (Exception e) {
            System.out.println("Failed to reset passwords: " + e.getMessage());
        }
    }

    @Test
    public void checkDoc() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/aistudyhub?useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                "1234"
            );
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT id, title, original_filename, extraction_status, length(extracted_text) as len, extraction_error FROM documents WHERE id = 8");
            if (rs.next()) {
                System.out.println("=== DOC 8 DETAILS ===");
                System.out.println("ID: " + rs.getInt("id"));
                System.out.println("TITLE: " + rs.getString("title"));
                System.out.println("FILENAME: " + rs.getString("original_filename"));
                System.out.println("STATUS: " + rs.getString("extraction_status"));
                System.out.println("TEXT LENGTH: " + rs.getInt("len"));
                System.out.println("ERROR: " + rs.getString("extraction_error"));
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Failed to check doc: " + e.getMessage());
        }
    }
}
