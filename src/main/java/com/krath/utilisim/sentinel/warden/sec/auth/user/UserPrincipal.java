package com.krath.utilisim.sentinel.warden.sec.auth.user;

import lombok.Getter;

@Getter
public class UserPrincipal {
    private final String id;

    public UserPrincipal(String id) {
        this.id = id;
    }

}
