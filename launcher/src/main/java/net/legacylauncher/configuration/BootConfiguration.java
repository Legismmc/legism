package net.legacylauncher.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BootConfiguration {
    private boolean stats, ely;
    private final Map<String, List<String>> repositories = new HashMap<>();
    private final Map<String, String> feedback = new HashMap<>();

    public boolean isStatsAllowed() {
        return stats;
    }

    public boolean isElyAllowed() {
        return ely;
    }

    public Map<String, List<String>> getRepositories() {
        return repositories;
    }

    public Map<String, String> getFeedback() {
        return feedback;
    }

    public static BootConfiguration parse(String options) {
        Objects.requireNonNull(options, "options");

        Gson gson = new GsonBuilder().create();

        return gson.fromJson(options, BootConfiguration.class);
    }
}
