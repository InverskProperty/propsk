
// Create this file if it doesn't exist: UserService.java
package site.easy.to.build.crm.service.ticket.user;

import site.easy.to.build.crm.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserService {
    User findById(Long id);
    List<User> findAll();
    User save(User user);
    void delete(User user);
    boolean existsById(Long id);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
}