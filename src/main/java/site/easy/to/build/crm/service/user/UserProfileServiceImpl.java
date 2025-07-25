package site.easy.to.build.crm.service.user;

import org.springframework.stereotype.Service;
import site.easy.to.build.crm.entity.UserProfile;
import site.easy.to.build.crm.repository.UserProfileRepository;

import java.util.List;
import java.util.Optional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;

    public UserProfileServiceImpl(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    public Optional<UserProfile> findById(Long id) {    // Fixed: return type should be Optional<UserProfile>
        return userProfileRepository.findById(id.intValue());
    }

    @Override
    public UserProfile save(UserProfile userProfile) {
        return userProfileRepository.save(userProfile);
    }

    @Override
    public UserProfile findByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId.intValue());
    }

    @Override
    public List<UserProfile> getAllProfiles() {
        return userProfileRepository.findAll();
    }
}