package com.krath.utilisim.ledger.scribe;

import com.krath.utilisim.ledger.tome.Tome;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


public interface Scribe extends JpaRepository<Tome, String> {
    List<Tome> findByUserId(String userId);
}
