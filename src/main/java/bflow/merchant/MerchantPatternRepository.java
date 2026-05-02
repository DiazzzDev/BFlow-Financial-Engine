package bflow.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MerchantPatternRepository
        extends JpaRepository<MerchantPattern, UUID> {

    /**
     * Find all merchant patterns ordered by priority in descending order.
     *
     * @return list of merchant patterns ordered by priority
     */
    List<MerchantPattern> findAllByOrderByPriorityDesc();
}
