package net.legacylauncher.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PKCE is the only thing standing between an intercepted authorisation code and someone
 * else's Ely.by account now that the client ships without a secret, and a challenge that
 * does not match its verifier fails at the far end with nothing useful to say. Worth
 * checking against the rules in RFC 7636 rather than against itself.
 */
class ElyPkceTest {

    @Test
    @DisplayName("the challenge is the base64url SHA-256 of the verifier")
    void challengeMatchesVerifier() throws Exception {
        ElyPkce pkce = new ElyPkce();

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(pkce.getVerifier().getBytes(StandardCharsets.US_ASCII));
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);

        assertEquals(expected, pkce.getChallenge());
        assertEquals("S256", pkce.getChallengeMethod());
    }

    @Test
    @DisplayName("the verifier is the length and alphabet the spec allows")
    void verifierIsWellFormed() {
        String verifier = new ElyPkce().getVerifier();
        assertTrue(verifier.length() >= 43 && verifier.length() <= 128,
                "verifier length out of range: " + verifier.length());
        // RFC 7636 section 4.1: unreserved characters only, so nothing needing escaping
        assertTrue(verifier.matches("[A-Za-z0-9\\-._~]+"), "unexpected characters: " + verifier);
    }

    @Test
    @DisplayName("the challenge carries nothing that would need escaping in a URL")
    void challengeIsUrlSafe() {
        String challenge = new ElyPkce().getChallenge();
        assertTrue(challenge.matches("[A-Za-z0-9\\-_]+"), "not url-safe: " + challenge);
    }

    @Test
    @DisplayName("every sign-in gets its own verifier")
    void verifiersAreNotReused() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            seen.add(new ElyPkce().getVerifier());
        }
        assertEquals(50, seen.size(), "verifiers repeated");
        assertNotEquals(new ElyPkce().getVerifier(), new ElyPkce().getChallenge());
    }
}
