package com.amannmalik.acp.server.security;

import jakarta.servlet.http.HttpServletRequest;

public interface RequestAuthenticator {
    void authenticate(HttpServletRequest request, byte[] body);
}
