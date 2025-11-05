package site.easy.to.build.crm.service.property;

import site.easy.to.build.crm.entity.Property;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for managing property vacancy workflows.
 * Handles notice given, advertising, and availability tracking.
 */
public interface PropertyVacancyService {

    /**
     * Mark property as having notice given by tenant
     *
     * @param propertyId Property ID
     * @param noticeDate Date notice was given
     * @param expectedVacancyDate Expected date property will be vacant
     * @return Updated property
     */
    Property markNoticeGiven(Long propertyId, LocalDate noticeDate, LocalDate expectedVacancyDate);

    /**
     * Start advertising a property
     * Auto-creates advertising start date and updates occupancy status
     *
     * @param propertyId Property ID
     * @return Updated property
     */
    Property startAdvertising(Long propertyId);

    /**
     * Mark property as available for letting
     *
     * @param propertyId Property ID
     * @param availableFrom Date property is available from
     * @return Updated property
     */
    Property markAvailable(Long propertyId, LocalDate availableFrom);

    /**
     * Mark property as occupied (after tenant moves in)
     * Clears all vacancy-related dates
     *
     * @param propertyId Property ID
     * @return Updated property
     */
    Property markOccupied(Long propertyId);

    /**
     * Put property under maintenance
     *
     * @param propertyId Property ID
     * @return Updated property
     */
    Property markUnderMaintenance(Long propertyId);

    /**
     * Take property off market (not available for letting)
     *
     * @param propertyId Property ID
     * @return Updated property
     */
    Property markOffMarket(Long propertyId);

    /**
     * Get all properties with notice given
     *
     * @return List of properties with notice given
     */
    List<Property> getPropertiesWithNoticeGiven();

    /**
     * Get all properties currently being advertised
     *
     * @return List of properties being advertised
     */
    List<Property> getAdvertisingProperties();

    /**
     * Get all properties available for letting
     *
     * @return List of available properties
     */
    List<Property> getAvailableProperties();

    /**
     * Get all properties requiring marketing attention
     * (Notice given or currently advertising)
     *
     * @return List of properties requiring attention
     */
    List<Property> getPropertiesRequiringMarketingAttention();

    /**
     * Get properties with expected vacancy within date range
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of properties becoming vacant in range
     */
    List<Property> getPropertiesVacantBetween(LocalDate startDate, LocalDate endDate);

    /**
     * Get properties with urgent vacancy (becoming vacant soon)
     *
     * @param withinDays Number of days to check
     * @return List of properties becoming vacant within specified days
     */
    List<Property> getPropertiesWithUrgentVacancy(int withinDays);

    /**
     * Get properties available by a specific date
     *
     * @param date Date to check availability
     * @return List of properties available by that date
     */
    List<Property> getPropertiesAvailableByDate(LocalDate date);

    /**
     * Find suitable properties for a lead's requirements
     *
     * @param bedrooms Number of bedrooms required (null for any)
     * @param maxRent Maximum monthly rent (null for any)
     * @param availableFrom Desired availability date (null for any)
     * @return List of matching properties
     */
    List<Property> findSuitablePropertiesForLead(Integer bedrooms, java.math.BigDecimal maxRent, LocalDate availableFrom);

    /**
     * Get properties with stale advertising (been advertising for > threshold days)
     *
     * @param days Number of days threshold
     * @return List of properties with stale advertising
     */
    List<Property> getPropertiesWithStaleAdvertising(int days);

    /**
     * Check if property is available for letting
     *
     * @param propertyId Property ID
     * @return true if available for letting
     */
    boolean isPropertyAvailableForLetting(Long propertyId);

    /**
     * Get vacancy dashboard statistics
     * Returns counts by occupancy status
     *
     * @return Map of status to counts
     */
    java.util.Map<String, java.util.Map<String, Long>> getVacancyDashboardStats();
}
