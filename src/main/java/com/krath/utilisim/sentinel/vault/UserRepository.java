package com.krath.utilisim.sentinel.vault;

import com.krath.utilisim.sentinel.foundation.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, String> {
    AppUser findByUsername(String username);

    AppUser findByEmail(String email);
}
