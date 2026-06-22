package com.krath.utilisim.sentinel.smithy.user;

import com.krath.utilisim.sentinel.foundation.AppUser;

import java.util.Optional;

public interface UserService
{
    //register user
    AppUser registerUser(AppUser user);

    Optional<AppUser> findByEmail(String email);

}
