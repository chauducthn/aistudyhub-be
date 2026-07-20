package com.studyhub.aistudyhubbe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private String provider = "qwen";
    private OpenAi openai = new OpenAi();
    private Qwen qwen = new Qwen();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public OpenAi getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAi openai) {
        this.openai = openai;
    }

    public Qwen getQwen() {
        return qwen;
    }

    public void setQwen(Qwen qwen) {
        this.qwen = qwen;
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

    public static class Qwen {

        private String apiKey = "";
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String model = "qwen/qwen3.6-flash";
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
