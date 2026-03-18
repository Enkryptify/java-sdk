package com.enkryptify.exception;

public class SecretNotFoundException extends EnkryptifyException {

    private final String key;
    private final String workspace;
    private final String environment;

    public SecretNotFoundException(String key, String workspace, String environment) {
        super("Secret \"" + key + "\" not found in workspace \"" + workspace + "\" (environment: \"" + environment + "\"). " +
              "Verify the secret exists in your Enkryptify dashboard.\n" +
              "Docs: https://docs.enkryptify.com/sdk/troubleshooting#secret-not-found");
        this.key = key;
        this.workspace = workspace;
        this.environment = environment;
    }

    public String getKey() {
        return key;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getEnvironment() {
        return environment;
    }
}
