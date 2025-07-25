package site.easy.to.build.crm.service.user;

import site.easy.to.build.crm.entity.UserProfile;

import java.util.List;
import java.util.Optional;

public interface UserProfileService {

    public Optional<UserProfile> findById(Long id);  // Changed int to Long

    public UserProfile save(UserProfile userProfile);

    public UserProfile findByUserId(Long userId);    // Changed int to Long

    public List<UserProfile> getAllProfiles();
}