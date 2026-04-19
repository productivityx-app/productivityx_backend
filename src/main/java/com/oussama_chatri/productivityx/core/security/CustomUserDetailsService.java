package com.oussama_chatri.productivityx.core.security;

import com.oussama_chatri.productivityx.core.user.User;
import com.oussama_chatri.productivityx.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found for identifier: " + identifier));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                true,  // accountNonExpired
                true,  // credentialsNonExpired
                user.getLockedUntil() == null || user.getLockedUntil().isBefore(Instant.now()),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
