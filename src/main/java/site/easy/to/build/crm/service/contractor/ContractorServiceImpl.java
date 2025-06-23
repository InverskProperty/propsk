package site.easy.to.build.crm.service.contractor;

import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.Contractor;
import site.easy.to.build.crm.repository.ContractorRepository;
import java.util.List;
import java.util.Optional;

@Service
public class ContractorServiceImpl implements ContractorService {
    
    private final ContractorRepository contractorRepository;
    
    public ContractorServiceImpl(ContractorRepository contractorRepository) {
        this.contractorRepository = contractorRepository;
    }
    
    @Override
    public List<Contractor> findAll() {
        return contractorRepository.findAll();
    }
    
    @Override
    public Contractor findById(Long id) {
        return contractorRepository.findById(id).orElse(null);
    }
    
    @Override
    public Optional<Contractor> findByEmailAddress(String emailAddress) {
        return contractorRepository.findByEmailAddress(emailAddress);
    }
    
    @Override
    public Contractor save(Contractor contractor) {
        return contractorRepository.save(contractor);
    }
    
    @Override
    public void delete(Contractor contractor) {
        contractorRepository.delete(contractor);
    }
    
    @Override
    public List<Contractor> findByStatus(String status) {
        return contractorRepository.findByStatus(status);
    }
    
    @Override
    public List<Contractor> findAvailableContractors() {
        return contractorRepository.findByStatus("AVAILABLE");
    }
}