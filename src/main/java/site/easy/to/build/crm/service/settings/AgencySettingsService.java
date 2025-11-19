package site.easy.to.build.crm.service.settings;

import site.easy.to.build.crm.entity.AgencySettings;

import java.util.Optional;

public interface AgencySettingsService {

    /**
     * Get the agency settings
     * @return Optional containing agency settings
     */
    Optional<AgencySettings> getSettings();

    /**
     * Update agency settings
     * @param settings The agency settings to save
     * @return The saved agency settings
     */
    AgencySettings updateSettings(AgencySettings settings);

    /**
     * Clear cached settings (call after update)
     */
    void clearCache();
}
