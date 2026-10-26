package com.example.mustudentsync;

import static org.junit.Assert.*;

import com.example.mustudentsync.session.SessionRules;
import org.junit.Test;

public class SessionRulesTest {

    @Test
    public void expiredTokenOnProtectedRouteEndsSession() {
        assertTrue(SessionRules.isSessionExpired(401, "/api/students"));
        assertTrue(SessionRules.isSessionExpired(401, "/api/me"));
    }

    @Test
    public void wrongPasswordOnLoginIsNotExpiry() {
        assertFalse(SessionRules.isSessionExpired(401, "/api/auth/login"));
    }

    @Test
    public void otherStatusesAreNotExpiry() {
        assertFalse(SessionRules.isSessionExpired(403, "/api/students"));
        assertFalse(SessionRules.isSessionExpired(200, "/api/me"));
    }

    @Test
    public void unknownPathWith401IsTreatedAsExpired() {
        assertTrue(SessionRules.isSessionExpired(401, null));
    }
}