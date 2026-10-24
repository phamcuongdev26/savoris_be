package com.savoris.security.jwt;

import com.savoris.constant.StaffStatus;
import com.savoris.repository.StaffRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                try {
                    // Validate token and extract staffId
                    Integer staffId = jwtService.validateAccessToken(jwt);

                    if (staffId != null) {
                        // Load staff from database
                        var staff = staffRepository.findById(staffId).orElse(null);

                        // Resigned staff lose access at once, even with a token that has not expired yet
                        if (staff != null && staff.getStatus() == StaffStatus.ACTIVE) {
                            // Create authentication token
                            var authority = new SimpleGrantedAuthority("ROLE_" + staff.getRole().name());
                            UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                    staffId,
                                    null,
                                    java.util.Collections.singletonList(authority)
                                );

                            authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                            );

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            log.debug("Set Security Context for staff: {}", staffId);
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Invalid JWT token: {}", ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     * Expected format: "Bearer <token>"
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("savoris_access".equals(cookie.getName())) return cookie.getValue();
            }
        }
        return null;
    }
}
