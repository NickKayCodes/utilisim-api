package com.krath.utilisim.sentinel.foundation.scrolls.user;

import com.krath.utilisim.sentinel.foundation.AppUser;

import java.util.Objects;

/**
 * Safe, non-sensitive view of a user account.
 *
 * <p>This is the only user shape controllers may return. It deliberately has no
 * password field, so a future change to {@link AppUser} cannot leak credential
 * material through an HTTP response by accident.</p>
 */
public record UserResponse(String id, String username, String email) {

    /** Projects an entity onto the public shape, discarding the password hash. */
    public static UserResponse from(AppUser user) {
        Objects.requireNonNull(user, "user");
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
