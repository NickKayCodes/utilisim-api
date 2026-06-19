package com.krath.utilisim.sentinel.smithy.user;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.vault.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repo;

    public UserServiceImpl(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public AppUser registerUser(AppUser user) {
        return repo.save(user);
    }

    @Override
    public AppUser findByEmail(String email) {
        return repo.findByEmail(email);
    }

    @Override
    public AppUser findByUsername(String username) {
        return repo.findByUsername(username);
    }
}
