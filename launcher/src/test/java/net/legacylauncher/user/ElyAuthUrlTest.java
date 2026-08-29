package net.legacylauncher.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authorisation URL is built by string formatting, where a placeholder in the wrong
 * place produces a URL that looks plausible and is rejected by the far end with something
 * unhelpful. This prints and checks the real thing.
 */
class ElyAuthUrlTest {

    @Test
    @DisplayName("the URL carries every parameter, in usable form")
    void urlIsWellFormed() throws Exception {
        ElyPkce pkce = new ElyPkce();
        String redirect = "http://localhost:54321/";
        String url = String.format(Locale.ROOT, ElyAuthFlow.OAUTH2_AUTH_REQUEST,
                URLEncoder.encode(redirect, StandardCharsets.UTF_8.name()),
                12345,
                pkce.getChallenge(),
                pkce.getChallengeMethod());

        System.out.println("AUTH URL: " + url);

        assertTrue(url.contains("client_id=legism"), url);
        assertTrue(url.contains("response_type=code"), url);
        assertTrue(url.contains("code_challenge=" + pkce.getChallenge()), url);
        assertTrue(url.contains("code_challenge_method=S256"), url);
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A54321%2F"), url);
        assertTrue(url.contains("state=12345"), url);
        assertTrue(!url.contains("prompt="), "prompt is undocumented and breaks the public-client flow: " + url);
        // no placeholder left behind, and nothing formatted into the wrong slot
        assertTrue(!url.contains("%s") && !url.contains("%d"), url);
    }
}
