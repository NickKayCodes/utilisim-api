package com.krath.utilisim.sentinel.foundation.scrolls.login;

//data transfer objects should be immutable for login
public record LoginRequest (String email, String password) {

}
