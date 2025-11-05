package site.easy.to.build.crm.service.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.OccupancyStatus;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyVacancyTask;
import site.easy.to.build.crm.repository.PropertyRepository;
import site.easy.to.build.crm.repository.PropertyVacancyTaskRepository;

import java.time.LocalDate;
import java.util.*;

/**
 * Implementation of PropertyVacancyService.
 * Handles property vacancy workflows including notice given, advertising, and availability.
 */
@Service
public class PropertyVacancyServiceImpl implements PropertyVacancyService {

    private final PropertyRepository propertyRepository;
    private final PropertyVacancyTaskRepository taskRepository;

    public PropertyVacancyServiceImpl(PropertyRepository propertyRepository,
                                       PropertyVacancyTaskRepository taskRepository) {
        this.propertyRepository = propertyRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public Property markNoticeGiven(Long propertyId, LocalDate noticeDate, LocalDate expectedVacancyDate) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.markNoticeGiven(noticeDate, expectedVacancyDate);
        Property saved = propertyRepository.save(property);

        // Auto-create vacancy tasks
        createVacancyTasks(saved);

        return saved;
    }

    @Override
    @Transactional
    public Property startAdvertising(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.startAdvertising();
        return propertyRepository.save(property);
    }

    @Override
    @Transactional
    public Property markAvailable(Long propertyId, LocalDate availableFrom) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.markAvailable(availableFrom);
        return propertyRepository.save(property);
    }

    @Override
    @Transactional
    public Property markOccupied(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.markOccupied();
        Property saved = propertyRepository.save(property);

        // Cancel any pending vacancy tasks
        cancelPendingVacancyTasks(saved);

        return saved;
    }

    @Override
    @Transactional
    public Property markUnderMaintenance(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.setOccupancyStatus(OccupancyStatus.MAINTENANCE);
        return propertyRepository.save(property);
    }

    @Override
    @Transactional
    public Property markOffMarket(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        property.setOccupancyStatus(OccupancyStatus.OFF_MARKET);
        return propertyRepository.save(property);
    }

    @Override
    public List<Property> getPropertiesWithNoticeGiven() {
        return propertyRepository.findPropertiesWithNoticeGiven();
    }

    @Override
    public List<Property> getAdvertisingProperties() {
        return propertyRepository.findPropertiesBeingAdvertised();
    }

    @Override
    public List<Property> getAvailableProperties() {
        return propertyRepository.findPropertiesAvailableForLetting();
    }

    @Override
    public List<Property> getPropertiesRequiringMarketingAttention() {
        return propertyRepository.findPropertiesRequiringMarketingAttention();
    }

    @Override
    public List<Property> getPropertiesVacantBetween(LocalDate startDate, LocalDate endDate) {
        return propertyRepository.findByExpectedVacancyDateBetween(startDate, endDate);
    }

    @Override
    public List<Property> getPropertiesWithUrgentVacancy(int withinDays) {
        LocalDate cutoffDate = LocalDate.now().plusDays(withinDays);
        return propertyRepository.findPropertiesWithUrgentVacancy(cutoffDate);
    }

    @Override
    public List<Property> getPropertiesAvailableByDate(LocalDate date) {
        return propertyRepository.findPropertiesAvailableByDate(date);
    }

    @Override
    public List<Property> findSuitablePropertiesForLead(Integer bedrooms, java.math.BigDecimal maxRent, LocalDate availableFrom) {
        return propertyRepository.findSuitablePropertiesForLead(bedrooms, maxRent, availableFrom);
    }

    @Override
    public List<Property> getPropertiesWithStaleAdvertising(int days) {
        LocalDate cutoffDate = LocalDate.now().minusDays(days);
        return propertyRepository.findPropertiesWithStaleAdvertising(cutoffDate);
    }

    @Override
    public boolean isPropertyAvailableForLetting(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with ID: " + propertyId));

        return property.isAvailableForLetting();
    }

    @Override
    public Map<String, Map<String, Long>> getVacancyDashboardStats() {
        List<Object[]> results = propertyRepository.getVacancyDashboardStats();
        Map<String, Map<String, Long>> stats = new HashMap<>();

        for (Object[] row : results) {
            String status = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            Long urgentCount = ((Number) row[2]).longValue();

            Map<String, Long> statusStats = new HashMap<>();
            statusStats.put("total", count);
            statusStats.put("urgent", urgentCount);

            stats.put(status, statusStats);
        }

        return stats;
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    /**
     * Auto-create vacancy tasks when notice is given
     */
    private void createVacancyTasks(Property property) {
        LocalDate vacancyDate = property.getExpectedVacancyDate();
        if (vacancyDate == null) {
            return;
        }

        // Task 1: Property Inspection (2 weeks before vacancy)
        if (vacancyDate.isAfter(LocalDate.now().plusDays(14))) {
            PropertyVacancyTask inspectionTask = new PropertyVacancyTask();
            inspectionTask.setProperty(property);
            inspectionTask.setTaskType("INSPECTION");
            inspectionTask.setTitle("Property Inspection - " + property.getPropertyName());
            inspectionTask.setDescription("Conduct property inspection before tenant vacates. Check for any damages or maintenance needs.");
            inspectionTask.setDueDate(vacancyDate.minusDays(14));
            inspectionTask.setPriority("MEDIUM");
            inspectionTask.setAutoCreated(true);
            inspectionTask.setTriggerEvent("NOTICE_GIVEN");
            taskRepository.save(inspectionTask);
        }

        // Task 2: Photography Session (1 week before vacancy)
        if (vacancyDate.isAfter(LocalDate.now().plusDays(7))) {
            PropertyVacancyTask photoTask = new PropertyVacancyTask();
            photoTask.setProperty(property);
            photoTask.setTaskType("PHOTOGRAPHY");
            photoTask.setTitle("Property Photography - " + property.getPropertyName());
            photoTask.setDescription("Arrange professional photography for property listing.");
            photoTask.setDueDate(vacancyDate.minusDays(7));
            photoTask.setPriority("HIGH");
            photoTask.setAutoCreated(true);
            photoTask.setTriggerEvent("NOTICE_GIVEN");
            taskRepository.save(photoTask);
        }

        // Task 3: Listing Creation (3 days after vacancy)
        PropertyVacancyTask listingTask = new PropertyVacancyTask();
        listingTask.setProperty(property);
        listingTask.setTaskType("LISTING_CREATION");
        listingTask.setTitle("Create Property Listing - " + property.getPropertyName());
        listingTask.setDescription("Create property listing with photos, description, and pricing.");
        listingTask.setDueDate(vacancyDate.plusDays(3));
        listingTask.setPriority("HIGH");
        listingTask.setAutoCreated(true);
        listingTask.setTriggerEvent("NOTICE_GIVEN");
        taskRepository.save(listingTask);

        // Task 4: Key Handover (on vacancy date)
        PropertyVacancyTask keyTask = new PropertyVacancyTask();
        keyTask.setProperty(property);
        keyTask.setTaskType("KEY_HANDOVER");
        keyTask.setTitle("Key Handover - " + property.getPropertyName());
        keyTask.setDescription("Arrange key collection from outgoing tenant.");
        keyTask.setDueDate(vacancyDate);
        keyTask.setPriority("URGENT");
        keyTask.setAutoCreated(true);
        keyTask.setTriggerEvent("NOTICE_GIVEN");
        taskRepository.save(keyTask);

        // Task 5: Cleaning (2 days after vacancy)
        PropertyVacancyTask cleaningTask = new PropertyVacancyTask();
        cleaningTask.setProperty(property);
        cleaningTask.setTaskType("CLEANING");
        cleaningTask.setTitle("Property Cleaning - " + property.getPropertyName());
        cleaningTask.setDescription("Arrange professional cleaning after tenant vacates.");
        cleaningTask.setDueDate(vacancyDate.plusDays(2));
        cleaningTask.setPriority("HIGH");
        cleaningTask.setAutoCreated(true);
        cleaningTask.setTriggerEvent("NOTICE_GIVEN");
        taskRepository.save(cleaningTask);
    }

    /**
     * Cancel pending vacancy tasks when property is marked as occupied
     */
    private void cancelPendingVacancyTasks(Property property) {
        List<PropertyVacancyTask> pendingTasks = taskRepository.findActiveTasksForProperty(property);

        for (PropertyVacancyTask task : pendingTasks) {
            task.cancel();
            taskRepository.save(task);
        }
    }
}
