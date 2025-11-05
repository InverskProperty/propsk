package site.easy.to.build.crm.service.property;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyViewing;
import site.easy.to.build.crm.entity.User;
import site.easy.to.build.crm.repository.PropertyViewingRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of PropertyViewingService.
 * Handles property viewing scheduling and management.
 */
@Service
public class PropertyViewingServiceImpl implements PropertyViewingService {

    private final PropertyViewingRepository viewingRepository;

    public PropertyViewingServiceImpl(PropertyViewingRepository viewingRepository) {
        this.viewingRepository = viewingRepository;
    }

    @Override
    @Transactional
    public PropertyViewing scheduleViewing(Lead lead, Property property, LocalDateTime scheduledDateTime,
                                           Integer durationMinutes, String viewingType) {
        PropertyViewing viewing = new PropertyViewing(lead, property, scheduledDateTime);
        viewing.setDurationMinutes(durationMinutes != null ? durationMinutes : 30);
        viewing.setViewingType(viewingType != null ? viewingType : "IN_PERSON");
        viewing.setStatus("SCHEDULED");

        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing scheduleViewing(Lead lead, Property property, LocalDateTime scheduledDateTime,
                                           Integer durationMinutes, String viewingType, User assignedUser) {
        PropertyViewing viewing = scheduleViewing(lead, property, scheduledDateTime, durationMinutes, viewingType);
        viewing.setAssignedToUser(assignedUser);
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing rescheduleViewing(Long viewingId, LocalDateTime newDateTime) {
        PropertyViewing viewing = getViewingById(viewingId);

        if (!viewing.canBeRescheduled()) {
            throw new IllegalStateException("Viewing cannot be rescheduled. Current status: " + viewing.getStatus());
        }

        viewing.setScheduledDatetime(newDateTime);
        viewing.setStatus("RESCHEDULED");
        viewing.setReminderSent(false); // Reset reminder flag

        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing cancelViewing(Long viewingId) {
        PropertyViewing viewing = getViewingById(viewingId);
        viewing.cancel();
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing markAsNoShow(Long viewingId) {
        PropertyViewing viewing = getViewingById(viewingId);
        viewing.markAsNoShow();
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing completeViewing(Long viewingId, String feedback, String interestedLevel) {
        PropertyViewing viewing = getViewingById(viewingId);
        viewing.complete(feedback, interestedLevel);
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public PropertyViewing confirmViewing(Long viewingId) {
        PropertyViewing viewing = getViewingById(viewingId);
        viewing.setStatus("CONFIRMED");
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public int sendViewingReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.plusHours(24);

        List<PropertyViewing> viewingsNeedingReminders =
            viewingRepository.findViewingsNeedingReminders(now, tomorrow);

        int count = 0;
        for (PropertyViewing viewing : viewingsNeedingReminders) {
            // TODO: Integrate with PropertyLeadEmailService to send actual email
            // For now, just mark as sent
            viewing.setReminderSent(true);
            viewingRepository.save(viewing);
            count++;
        }

        return count;
    }

    @Override
    public PropertyViewing getViewingById(Long viewingId) {
        return viewingRepository.findById(viewingId)
                .orElseThrow(() -> new IllegalArgumentException("Viewing not found with ID: " + viewingId));
    }

    @Override
    public List<PropertyViewing> getViewingsForLead(Lead lead) {
        return viewingRepository.findByLeadOrderByScheduledDatetimeDesc(lead);
    }

    @Override
    public List<PropertyViewing> getViewingsForProperty(Property property) {
        return viewingRepository.findByPropertyOrderByScheduledDatetimeDesc(property);
    }

    @Override
    public List<PropertyViewing> getUpcomingViewings() {
        LocalDateTime now = LocalDateTime.now();
        List<String> activeStatuses = Arrays.asList("SCHEDULED", "CONFIRMED");
        return viewingRepository.findUpcomingViewings(now, activeStatuses);
    }

    @Override
    public List<PropertyViewing> getTodaysViewingsForUser(User user) {
        return viewingRepository.findTodaysViewingsForUser(user);
    }

    @Override
    public List<PropertyViewing> getViewingsByStatus(String status) {
        return viewingRepository.findByStatusOrderByScheduledDatetimeDesc(status);
    }

    @Override
    public List<PropertyViewing> getViewingsNeedingReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrow = now.plusHours(24);
        return viewingRepository.findViewingsNeedingReminders(now, tomorrow);
    }

    @Override
    public List<PropertyViewing> getRecentViewings(int days) {
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(days);
        return viewingRepository.findRecentViewings(sinceDate);
    }

    @Override
    public List<PropertyViewing> getViewingsByInterestLevel(String interestedLevel) {
        return viewingRepository.findByInterestedLevel(interestedLevel);
    }

    @Override
    @Transactional
    public PropertyViewing save(PropertyViewing viewing) {
        return viewingRepository.save(viewing);
    }

    @Override
    @Transactional
    public void delete(PropertyViewing viewing) {
        viewingRepository.delete(viewing);
    }
}
