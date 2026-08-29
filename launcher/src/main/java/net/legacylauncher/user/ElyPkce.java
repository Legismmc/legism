package net.legacylauncher.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * One PKCE exchange, as described by RFC 7636.
 * <p>
 * Needed because the launcher's Ely.by application is registered as a public client, and a
 * public client has no secret to prove itself with - anything shipped inside a desktop app
 * can be read straight out of it, so Ely.by does not issue one. PKCE replaces that: a
 * random value is invented per sign-in, only its hash travels in the URL the browser opens,
 * and the value itself is sent later when the code is exchanged. Anyone who intercepts the
 * authorisation code cannot use it without that value.
 */
final class ElyPkce {

    /**
     * 32 random bytes, which base64url-encodes to 43 characters - the shortest length the
     * spec allows, and plenty.
     */
    private static final int VERIFIER_BYTES = 32;

    private final String verifier;
    private final String challenge;

    ElyPkce() {
        byte[] random = new byte[VERIFIER_BYTES];
        new SecureRandom().nextBytes(random);
        this.verifier = encode(random);
        this.challenge = encode(sha256(this.verifier));
    }

    /**
     * The secret half, sent only when the code is exchanged for a token.
     */
    String getVerifier() {
        return verifier;
    }

    /**
     * The public half, safe to put in the URL the browser opens.
     */
    String getChallenge() {
        return challenge;
    }

    /**
     * Always S256. The spec also allows sending the verifier itself as the challenge,
     * which defeats the point of having one.
     */
    String getChallengeMethod() {
        return "S256";
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            // required of every Java implementation
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
