package com.studyhub.aistudyhubbe.security;

import com.studyhub.aistudyhubbe.repository.UserRepository;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final long USER_CACHE_TTL_MS = 10_000L;

    private final UserRepository userRepository;
    private final ConcurrentMap<Long, CachedPrincipal> userByIdCache = new ConcurrentHashMap<>();

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByEmailIgnoreCase(username)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserDetails loadUserById(Long id) {
        long now = System.currentTimeMillis();
        CachedPrincipal cached = userByIdCache.get(id);
        if (cached != null && cached.expiresAtMs() > now) {
            return cached.principal();
        }
        if (cached != null) {
            userByIdCache.remove(id, cached);
        }

        UserPrincipal principal = userRepository.findById(id)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        userByIdCache.put(id, new CachedPrincipal(principal, now + USER_CACHE_TTL_MS));
        return principal;
    }

    private record CachedPrincipal(UserPrincipal principal, long expiresAtMs) {
    }
}
