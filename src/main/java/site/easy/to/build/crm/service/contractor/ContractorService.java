package site.easy.to.build.crm.service.contractor;

import site.easy.to.build.crm.entity.Contractor;
import java.util.List;
import java.util.Optional;

public interface ContractorService {
    List<Contractor> findAll();
    Contractor findById(Long id);
    Optional<Contractor> findByEmailAddress(String emailAddress);
    Contractor save(Contractor contractor);
    void delete(Contractor contractor);
    List<Contractor> findByStatus(String status);
    List<Contractor> findAvailableContractors();
}