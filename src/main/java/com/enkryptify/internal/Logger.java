package com.enkryptify.internal;

import com.enkryptify.EnkryptifyConfig;

public class Logger {

    private final int level;

    public Logger(EnkryptifyConfig.LogLevel level) {
        this.level = level.ordinal();
    }

    public void debug(String message) {
        if (level <= EnkryptifyConfig.LogLevel.DEBUG.ordinal()) {
            System.err.println("[Enkryptify] " + message);
        }
    }

    public void info(String message) {
        if (level <= EnkryptifyConfig.LogLevel.INFO.ordinal()) {
            System.err.println("[Enkryptify] " + message);
        }
    }

    public void warn(String message) {
        if (level <= EnkryptifyConfig.LogLevel.WARN.ordinal()) {
            System.err.println("[Enkryptify] " + message);
        }
    }

    public void error(String message) {
        if (level <= EnkryptifyConfig.LogLevel.ERROR.ordinal()) {
            System.err.println("[Enkryptify] " + message);
        }
    }
}
