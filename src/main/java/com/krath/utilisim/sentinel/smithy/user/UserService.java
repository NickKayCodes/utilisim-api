package com.krath.utilisim.sentinel.smithy.user;

import com.krath.utilisim.sentinel.foundation.AppUser;

public interface UserService
{
    //register user
    AppUser registerUser(AppUser user);

    AppUser findByEmail(String email);

    AppUser findByUsername(String username);
}
