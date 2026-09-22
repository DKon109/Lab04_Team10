package com.ooaf.eventticketing.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 255)
    // Never store or expose a user's raw password.
    private String passwordHash;

    // Store the enum name rather than its numeric position so reordering roles is safe.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(length = 200)
    private String societyName;

    // Required by JPA when reconstructing an entity from a database row.
    protected User() {
    }

    public User(String email, String passwordHash, UserRole role) {
        this(email, passwordHash, role, null);
    }

    public User(String email, String passwordHash, UserRole role, String societyName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.societyName = societyName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getSocietyName() {
        return societyName;
    }

    public void setSocietyName(String societyName) {
        this.societyName = societyName;
    }
}
