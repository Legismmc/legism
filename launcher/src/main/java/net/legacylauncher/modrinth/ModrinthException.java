package net.legacylauncher.modrinth;

import java.io.IOException;

/**
 * Thrown when Modrinth could not be reached or answered with something we cannot use.
 */
public class ModrinthException extends IOException {
    public ModrinthException(String message) {
        super(message);
    }

    public ModrinthException(String message, Throwable cause) {
        super(message, cause);
    }
}
