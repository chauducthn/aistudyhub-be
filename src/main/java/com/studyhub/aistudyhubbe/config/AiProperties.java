package com.studyhub.aistudyhubbe.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "auto";
    private boolean fallbackToLocal = true;
    private Gemini gemini = new Gemini();
    private OpenAi openai = new OpenAi();

    public List<String> getGeminiModelCandidates() {
        List<String> candidates = new ArrayList<>();
        addModelCandidate(candidates, gemini.getModel());
        if (gemini.getFallbackModels() != null) {
            for (String fallbackModel : gemini.getFallbackModels()) {
                addModelCandidate(candidates, fallbackModel);
            }
        }
        if (candidates.isEmpty()) {
            candidates.add("gemini-2.0-flash-lite");
        }
        return candidates;
    }

    private static void addModelCandidate(List<String> candidates, String model) {
        if (model == null || model.isBlank()) {
            return;
        }
        String normalized = model.trim();
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToLocal() {
        return fallbackToLocal;
    }

    public void setFallbackToLocal(boolean fallbackToLocal) {
        this.fallbackToLocal = fallbackToLocal;
    }

    public Gemini getGemini() {
        return gemini;
    }

    public void setGemini(Gemini gemini) {
        this.gemini = gemini;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAi openai) {
        this.openai = openai;
    }

    public static class Gemini {

        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String model = "gemini-2.0-flash-lite";
        private List<String> fallbackModels = new ArrayList<>(List.of(
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash",
                "gemini-flash-latest"));
        private double temperature = 0.3;
        private int maxOutputTokens = 4096;
        private int timeoutSeconds = 60;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<String> getFallbackModels() {
            return fallbackModels;
        }

        public void setFallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class OpenAi {

        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";
        private double temperature = 0.3;
        private int maxOutputTokens = 4096;
        private int timeoutSeconds = 60;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
