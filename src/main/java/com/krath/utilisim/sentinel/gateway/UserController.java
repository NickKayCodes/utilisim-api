package com.krath.utilisim.sentinel.gateway;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.smithy.user.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/sentinel/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service){
        this.service = service;
    }

    @PostMapping("/register")
    public AppUser register(@RequestBody AppUser user){
        return service.registerUser(user);
    }

    //implement find by email
    @GetMapping("/search/by-email")
    public AppUser findUserByEmail(@RequestBody AppUser user){
        return service.findByEmail(user.getEmail());
    }


}
