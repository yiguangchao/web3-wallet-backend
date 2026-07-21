package com.example.wallet.common.api;

import com.example.wallet.infrastructure.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditActorProvider {

    private final ObjectProvider<HttpServletRequest> requestProvider;

    public AuditActorProvider(ObjectProvider<HttpServletRequest> requestProvider) {
        this.requestProvider = requestProvider;
    }

    public AuditActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = authentication != null && authentication.getPrincipal() instanceof LoginUser user
                ? user : new LoginUser(0L, "SYSTEM", "SYSTEM");
        return new AuditActor(loginUser.getUserId(), loginUser.getUsername(), loginUser.getRole(), resolveIp());
    }

    private String resolveIp() {
        HttpServletRequest request = requestProvider.getIfAvailable();
        if (request == null) {
            return "SYSTEM";
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
