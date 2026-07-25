package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeRequest;
import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TomeService {

    @Autowired
    private Scribe scribe;

    public Tome createTome(TomeRequest request, String userId) {
        Tome tome = new Tome();
        tome.setUserId(userId);
        tome.setName(request.name());
        tome.setCurrentBalance(request.currentBalance());
        tome.setApr(request.apr());
        tome.setEstimatedApr(
                request.apr() == null ? (float)calculateEstimatedApr(request) : request.apr()
        );
        tome.setMinimumDebtPayment(request.minimumDebtPayment());
        tome.setCreditLimit(request.creditLimit());
        tome.setType(request.type());
        tome.setCreatedAt(OffsetDateTime.now());
        tome.setUpdatedAt(OffsetDateTime.now());

        return scribe.save(tome);
    }

    //calculate estimated apr
    // may need forge built to see the rest of the data for now we will return an arbitary apr rate of 16%
    private double calculateEstimatedApr(TomeRequest request) {
        double principal = 0.16;
        return principal;
    }

    public List<Tome> getAllTomesByUserId(String userId) {
        return scribe.findByUserId(userId);
    }

    public void deleteTomeById(String id, String userId) {
        Tome tome = scribe.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));

        if (!tome.getUserId().equals(userId)) {
            throw new RuntimeException("Forbidden");
        }

        scribe.delete(tome);
    }
}
