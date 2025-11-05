package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.Lead;
import site.easy.to.build.crm.entity.Property;
import site.easy.to.build.crm.entity.PropertyViewing;
import site.easy.to.build.crm.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for PropertyViewing entity.
 * Handles viewing appointment data access.
 */
@Repository
public interface PropertyViewingRepository extends JpaRepository<PropertyViewing, Long> {

    /**
     * Find all viewings for a specific lead
     */
    List<PropertyViewing> findByLeadOrderByScheduledDatetimeDesc(Lead lead);

    /**
     * Find all viewings for a specific property
     */
    List<PropertyViewing> findByPropertyOrderByScheduledDatetimeDesc(Property property);

    /**
     * Find viewings by property and specific statuses
     */
    List<PropertyViewing> findByPropertyAndStatusIn(Property property, List<String> statuses);

    /**
     * Find viewings scheduled between two dates
     */
    List<PropertyViewing> findByScheduledDatetimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find upcoming viewings (scheduled in the future)
     */
    @Query("SELECT pv FROM PropertyViewing pv WHERE pv.scheduledDatetime > :now AND pv.status IN :statuses ORDER BY pv.scheduledDatetime ASC")
    List<PropertyViewing> findUpcomingViewings(@Param("now") LocalDateTime now, @Param("statuses") List<String> statuses);

    /**
     * Find viewings assigned to a specific user
     */
    List<PropertyViewing> findByAssignedToUserOrderByScheduledDatetimeAsc(User user);

    /**
     * Find viewings by status
     */
    List<PropertyViewing> findByStatusOrderByScheduledDatetimeDesc(String status);

    /**
     * Find viewings that need reminders sent
     * (scheduled within next 24 hours and reminder not yet sent)
     */
    @Query("SELECT pv FROM PropertyViewing pv WHERE pv.scheduledDatetime BETWEEN :now AND :tomorrow " +
           "AND pv.reminderSent = false AND pv.status IN ('SCHEDULED', 'CONFIRMED') ORDER BY pv.scheduledDatetime ASC")
    List<PropertyViewing> findViewingsNeedingReminders(@Param("now") LocalDateTime now, @Param("tomorrow") LocalDateTime tomorrow);

    /**
     * Count viewings for a property
     */
    long countByProperty(Property property);

    /**
     * Count upcoming viewings for a property
     */
    @Query("SELECT COUNT(pv) FROM PropertyViewing pv WHERE pv.property = :property " +
           "AND pv.scheduledDatetime > :now AND pv.status IN ('SCHEDULED', 'CONFIRMED')")
    long countUpcomingViewingsForProperty(@Param("property") Property property, @Param("now") LocalDateTime now);

    /**
     * Find viewings by Google Calendar event ID
     */
    PropertyViewing findByGoogleCalendarEventId(String eventId);

    /**
     * Find all viewings created by a specific user
     */
    List<PropertyViewing> findByCreatedByOrderByCreatedAtDesc(User user);

    /**
     * Find recent viewings (last N days)
     */
    @Query("SELECT pv FROM PropertyViewing pv WHERE pv.scheduledDatetime >= :sinceDate ORDER BY pv.scheduledDatetime DESC")
    List<PropertyViewing> findRecentViewings(@Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Find completed viewings by interest level
     */
    @Query("SELECT pv FROM PropertyViewing pv WHERE pv.status = 'COMPLETED' AND pv.interestedLevel = :level ORDER BY pv.scheduledDatetime DESC")
    List<PropertyViewing> findByInterestedLevel(@Param("level") String level);

    /**
     * Get viewings for today for a specific user
     */
    @Query("SELECT pv FROM PropertyViewing pv WHERE pv.assignedToUser = :user " +
           "AND DATE(pv.scheduledDatetime) = CURRENT_DATE AND pv.status IN ('SCHEDULED', 'CONFIRMED') ORDER BY pv.scheduledDatetime ASC")
    List<PropertyViewing> findTodaysViewingsForUser(@Param("user") User user);
}
