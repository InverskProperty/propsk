package site.easy.to.build.crm.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class BeneficiaryTypeConverter implements AttributeConverter<BeneficiaryType, String> {
    
    private static final Logger logger = LoggerFactory.getLogger(BeneficiaryTypeConverter.class);
    
    @Override
    public String convertToDatabaseColumn(BeneficiaryType attribute) {
        return attribute != null ? attribute.name() : null;
    }
    
    @Override
    public BeneficiaryType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        
        try {
            String cleanData = dbData.trim().toUpperCase();
            return BeneficiaryType.valueOf(cleanData);
        } catch (IllegalArgumentException e) {
            logger.warn("⚠️ Unknown BeneficiaryType '{}', defaulting to BENEFICIARY", dbData);
            return BeneficiaryType.BENEFICIARY;
        }
    }
}