package org.ac.cst8277.williams.roy.filter;

import io.jsonwebtoken.ExpiredJwtException;
import org.ac.cst8277.williams.roy.service.JwtAuthenticationService;
import org.ac.cst8277.williams.roy.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private JwtAuthenticationService jwtAuthenticationService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");

        String username = null;
        String role = null;
        String jwtToken = null;
        String refreshToken = null;
        // JWT Token is in the form "Bearer token". Remove Bearer word and get
        // only the Token
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
                role = jwtAuthenticationService.getRoleFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                System.out.println("Unable to get JWT Token");
            } catch (ExpiredJwtException e) {
                System.out.println("JWT Token has expired");
                System.out.println("Attempting to validate using refresh token...");
                refreshToken = jwtAuthenticationService.getRefreshToken(jwtToken);
            }
        } else {
            logger.warn("JWT Token does not begin with Bearer String");
            response.sendError(401, "Unauthorized");
        }

        // if access token is expired generate a new one using the refresh token
        if (refreshToken != null) {
            username = jwtTokenUtil.getUsernameFromToken(refreshToken);
            role = jwtAuthenticationService.getRoleByRefreshToken(refreshToken);
            UserDetails userDetails = this.jwtAuthenticationService.loadUserByUsername(username);
            jwtToken = jwtTokenUtil.generateToken(userDetails);
            jwtAuthenticationService.updateToken(jwtToken, role).subscribe();
        }

        // Once we get the token validate it and make sure it's associated with a role
        if (username != null && role != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = this.jwtAuthenticationService.loadUserByUsername(username);

            // if token is valid configure Spring Security to manually set
            // authentication
            if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {

                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // After setting the Authentication in the context, we specify
                // that the current user is authenticated. So it passes the
                // Spring Security Configurations successfully.
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);

                // once authenticated forward the request
                chain.doFilter(request, response);
            } else {
                // if user can't be authenticated return 401 unauthorized
                logger.warn("Could not be authenticated");
                response.sendError(401, "Unauthorized");
            }
        }
    }

    // only filter requests to /authenticate/validate
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/authenticate/validate");
    }
}