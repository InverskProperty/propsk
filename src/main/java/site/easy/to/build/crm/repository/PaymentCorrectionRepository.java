package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.PaymentCorrection;

import java.util.List;

@Repository
public interface PaymentCorrectionRepository extends JpaRepository<PaymentCorrection, Long> {

    List<PaymentCorrection> findByActiveTrue();

    List<PaymentCorrection> findByActiveTrueOrderByCreatedAtDesc();

    List<PaymentCorrection> findByOriginalPropertyId(Long propertyId);

    List<PaymentCorrection> findByCorrectedPropertyId(Long propertyId);
}
