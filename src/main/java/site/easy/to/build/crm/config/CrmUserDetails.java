package site.easy.to.build.crm.config;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import site.easy.to.build.crm.repository.UserRepository;
import site.easy.to.build.crm.entity.User;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CrmUserDetails implements UserDetailsService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    HttpSession session;
    @Autowired
    private HttpServletResponse request;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("🔍 CrmUserDetails.loadUserByUsername() called for: " + username);

        String crmUsername, password;
        List<User> foundUsers = userRepository.findByUsername(username);
        System.out.println("   Found " + foundUsers.size() + " users with username: " + username);

        User user = foundUsers.size() == 1 ? foundUsers.get(0) : null;
        List<GrantedAuthority> authorities;
        if(user == null) {
            System.err.println("❌ No user found for username: " + username);
            throw new UsernameNotFoundException("user details not found for the user : " + username);
        } else {
            System.out.println("   User found - ID: " + user.getId() + ", Email: " + user.getEmail() + ", Status: " + user.getStatus());
            System.out.println("   Password length: " + (user.getPassword() != null ? user.getPassword().length() : "NULL"));
            System.out.println("   Roles count: " + (user.getRoles() != null ? user.getRoles().size() : "NULL"));
            if(user.getStatus().equals("suspended")) {
                HttpServletResponse httpServletResponse =
                        ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
                try {
                    assert httpServletResponse != null;
                    httpServletResponse.sendRedirect("/account-suspended");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            password = user.getPassword();
            if (password == null || password.trim().isEmpty()) {
                System.err.println("❌ User " + username + " has null or empty password");
                throw new UsernameNotFoundException("User password is not set for: " + username);
            }

            session.setAttribute("loggedInUserId", user.getId());
            authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority(role.getName()))
                    .collect(Collectors.toList());

            if (authorities.isEmpty()) {
                System.err.println("❌ User " + username + " has no roles assigned");
                throw new UsernameNotFoundException("User has no roles assigned: " + username);
            }

            System.out.println("✅ Loading user details for: " + username + " with " + authorities.size() + " roles");
        }

        return new org.springframework.security.core.userdetails.User(username, password, authorities);
    }
}
