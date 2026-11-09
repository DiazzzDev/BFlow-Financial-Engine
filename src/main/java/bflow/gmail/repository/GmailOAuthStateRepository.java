package bflow.gmail.repository;

import bflow.gmail.entity.GmailOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface GmailOAuthStateRepository extends JpaRepository<GmailOAuthState, String> {
    void deleteByExpiresAtBefore(Instant now);

    @Modifying
    @Transactional
    @Query("update GmailOAuthState s set s.consumedAt = :now where s.stateHash = :hash and s.consumedAt is null and s.expiresAt > :now")
    int consumeValid(@Param("hash") String hash, @Param("now") Instant now);
}
