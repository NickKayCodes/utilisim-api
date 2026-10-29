package com.krath.utilisim.ledger.gateway;

import com.krath.utilisim.ledger.leaf.create.TomeRequest;
import com.krath.utilisim.ledger.leaf.create.TomeResponse;
import com.krath.utilisim.ledger.tome.Tome;
import com.krath.utilisim.ledger.vault.TomeMapper;
import com.krath.utilisim.ledger.vault.TomeService;
import com.krath.utilisim.sentinel.foundation.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tomes")
public class TomeController {

    @Autowired
    private TomeService tomeService;

    @PostMapping
    public ResponseEntity<TomeResponse> createTome(
            @AuthenticationPrincipal AppUser user,
            @RequestBody TomeRequest request
    ) {
        Tome created = tomeService.createTome(request, user.getId());
        return ResponseEntity.ok(TomeMapper.toResponse(created));
    }

    @GetMapping
    public ResponseEntity<List<TomeResponse>> getMyTomes(
            @AuthenticationPrincipal AppUser user
    ) {
        List<Tome> tomes = tomeService.getAllTomesByUserId(user.getId());
        return ResponseEntity.ok(TomeMapper.toResponseList(tomes));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTome(
            @AuthenticationPrincipal AppUser user,
            @PathVariable String id
    ) {
        tomeService.deleteTomeById(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
