package site.easy.to.build.crm.service.settings;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.easy.to.build.crm.entity.AgencySettings;
import site.easy.to.build.crm.repository.AgencySettingsRepository;

import java.util.Optional;

@Service
@Transactional
public class AgencySettingsServiceImpl implements AgencySettingsService {

    @Autowired
    private AgencySettingsRepository agencySettingsRepository;

    // Simple in-memory cache for agency settings (rarely changes)
    private AgencySettings cachedSettings;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 300000; // 5 minutes

    @Override
    public Optional<AgencySettings> getSettings() {
        long currentTime = System.currentTimeMillis();

        // Return cached settings if still valid
        if (cachedSettings != null && (currentTime - lastCacheTime) < CACHE_DURATION_MS) {
            return Optional.of(cachedSettings);
        }

        // Load from database
        Optional<AgencySettings> settings = agencySettingsRepository.getSettings();

        // Update cache
        if (settings.isPresent()) {
            cachedSettings = settings.get();
            lastCacheTime = currentTime;
        }

        return settings;
    }

    @Override
    public AgencySettings updateSettings(AgencySettings settings) {
        // Ensure we're updating the single record (id = 1)
        settings.setId(1);
        AgencySettings saved = agencySettingsRepository.save(settings);
        clearCache();
        return saved;
    }

    @Override
    public void clearCache() {
        cachedSettings = null;
        lastCacheTime = 0;
    }
}
