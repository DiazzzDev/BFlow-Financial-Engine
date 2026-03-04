package bflow.tranfers;

import bflow.tranfers.entities.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RepositoryTransfers extends JpaRepository<Transfer, UUID> {

}
