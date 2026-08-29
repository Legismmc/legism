package net.legacylauncher.user;

import org.apache.commons.lang3.StringUtils;

public class FallbackElyAuthFlow extends ElyAuthFlow<FallbackElyAuthFlowListener> {
    static final String STATIC_PAGE_REDIRECT_NAME = "static_page_with_code";

    @Override
    protected ElyAuthCode fetchCode() throws InterruptedException {
        int state = generateState();
        // the browser used to be sent a second, freshly generated state while the code
        // below was checked against the first - two different numbers for the one sign-in
        openBrowser(STATIC_PAGE_REDIRECT_NAME, state);
        checkCancelled();
        for (FallbackElyAuthFlowListener listener : getListenerList()) {
            String result = join(listener.fallbackStrategyRequestedInput(this));
            if (StringUtils.isNotBlank(result)) {
                return new ElyAuthCode(result, STATIC_PAGE_REDIRECT_NAME, state, getCodeVerifier());
            }
        }
        throw new InterruptedException("no result in listeners");
    }

    @Override
    protected void onCancelled() {
    }
}
