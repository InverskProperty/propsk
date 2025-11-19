package site.easy.to.build.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import site.easy.to.build.crm.entity.AgencySettings;

import java.util.Optional;

@Repository
public interface AgencySettingsRepository extends JpaRepository<AgencySettings, Integer> {

    /**
     * Get the single agency settings record (id = 1)
     * @return Optional containing the agency settings
     */
    default Optional<AgencySettings> getSettings() {
        return findById(1);
    }
}
