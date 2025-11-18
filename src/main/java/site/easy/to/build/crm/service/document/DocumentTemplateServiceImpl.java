package site.easy.to.build.crm.service.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.DocumentTemplate;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.entity.enums.CustomerType;
import site.easy.to.build.crm.entity.enums.DocumentCategory;
import site.easy.to.build.crm.repository.DocumentTemplateRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DocumentTemplateServiceImpl implements DocumentTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentTemplateServiceImpl.class);

    @Autowired
    private DocumentTemplateRepository documentTemplateRepository;

    @Override
    public DocumentTemplate save(DocumentTemplate template) {
        logger.info("Saving document template: {}", template.getName());
        return documentTemplateRepository.save(template);
    }

    @Override
    public Optional<DocumentTemplate> findById(Long id) {
        return documentTemplateRepository.findById(id);
    }

    @Override
    public List<DocumentTemplate> findAllActive() {
        return documentTemplateRepository.findByActiveTrue();
    }

    @Override
    public List<DocumentTemplate> findByCategory(DocumentCategory category) {
        return documentTemplateRepository.findByCategoryAndActiveTrue(category);
    }

    @Override
    public List<DocumentTemplate> findApplicableToCustomerType(CustomerType customerType) {
        return documentTemplateRepository.findApplicableToCustomerType(customerType);
    }

    @Override
    public List<DocumentTemplate> findByCategoryAndCustomerType(DocumentCategory category, CustomerType customerType) {
        return documentTemplateRepository.findByCategoryAndCustomerType(category, customerType);
    }

    @Override
    public List<DocumentTemplate> searchByName(String searchTerm) {
        return documentTemplateRepository.searchByName(searchTerm);
    }

    @Override
    public List<DocumentTemplate> findByCreatedBy(User user) {
        return documentTemplateRepository.findByCreatedByAndActiveTrue(user);
    }

    @Override
    public void deactivate(Long templateId) {
        logger.info("Deactivating document template: {}", templateId);
        documentTemplateRepository.findById(templateId).ifPresent(template -> {
            template.setActive(false);
            documentTemplateRepository.save(template);
        });
    }

    @Override
    public void activate(Long templateId) {
        logger.info("Activating document template: {}", templateId);
        documentTemplateRepository.findById(templateId).ifPresent(template -> {
            template.setActive(true);
            documentTemplateRepository.save(template);
        });
    }

    @Override
    public void delete(Long templateId) {
        logger.info("Deleting document template: {}", templateId);
        documentTemplateRepository.deleteById(templateId);
    }

    @Override
    public boolean isGoogleDocsTemplateIdInUse(String googleDocsTemplateId) {
        return documentTemplateRepository.findByGoogleDocsTemplateId(googleDocsTemplateId).isPresent();
    }

    @Override
    public List<String> getAvailableMergeFields() {
        return Arrays.asList(
            "customer_name",
            "customer_email",
            "customer_phone",
            "customer_address",
            "customer_city",
            "customer_state",
            "customer_country",
            "customer_postcode",
            "customer_type",
            "property_address",
            "property_postcode",
            "block_name",
            "rent_amount",
            "lease_start_date",
            "lease_end_date",
            "current_date",
            "current_date_long",
            "landlord_name",
            "landlord_address",
            "agent_name",
            "agent_address"
        );
    }
}
