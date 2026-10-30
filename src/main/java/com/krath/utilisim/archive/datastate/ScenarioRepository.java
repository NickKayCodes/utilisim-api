package com.krath.utilisim.archive.datastate;

import com.krath.utilisim.archive.scenario.ScenarioContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Every query is owner-scoped. There is deliberately no unscoped read or delete
 * helper: a handler that forgets the user id will not compile.
 */
public interface ScenarioRepository extends JpaRepository<ScenarioContext, String> {

    List<ScenarioContext> findByUserId(String userId);

    Optional<ScenarioContext> findByIdAndUserId(String id, String userId);

    boolean existsByIdAndUserId(String id, String userId);

    /** Derived delete queries are not transactional by default. */
    @Transactional
    void deleteByIdAndUserId(String id, String userId);
}
