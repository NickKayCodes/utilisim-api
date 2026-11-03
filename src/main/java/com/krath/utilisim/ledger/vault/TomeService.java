package com.krath.utilisim.ledger.vault;

import com.krath.utilisim.ledger.leaf.create.TomeRequest;
import com.krath.utilisim.ledger.scribe.Scribe;
import com.krath.utilisim.ledger.tome.Tome;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TomeService {

    /**
     * Fallback APR when the client does not supply one, in the API's percent
     * convention: {@code 16.0} means 16% (DEFECTS API-07).
     */
    static final double ESTIMATED_APR_PERCENT = 16.0;

    @Autowired
    private Scribe scribe;

    public Tome createTome(TomeRequest request, String userId) {
        BigDecimal minimum = money(request.minimumDebtPayment());

        BigDecimal currentPayment = request.currentPayment() == null
                ? minimum
                : money(request.currentPayment());
        if (currentPayment.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Current payment cannot be below the minimum payment");
        }

        Tome tome = new Tome();
        tome.setUserId(userId);
        tome.setName(request.name());
        tome.setCurrentBalance(money(request.currentBalance()));
        tome.setApr(request.apr() == null ? null : request.apr().setScale(4, RoundingMode.HALF_UP));
        tome.setEstimatedApr(request.apr() == null
                ? BigDecimal.valueOf(ESTIMATED_APR_PERCENT).setScale(4, RoundingMode.HALF_UP)
                : request.apr().setScale(4, RoundingMode.HALF_UP));
        tome.setMinimumDebtPayment(minimum);
        tome.setCurrentPayment(currentPayment);
        tome.setCreditLimit(money(request.creditLimit()));
        tome.setType(request.type());
        tome.setCreatedAt(OffsetDateTime.now());
        tome.setUpdatedAt(OffsetDateTime.now());

        return scribe.save(tome);
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

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
