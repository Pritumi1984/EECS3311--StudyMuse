package com.studymuse;

import com.studymuse.config.AppConfig;

/** Entry point. "cli" starts the command line; anything else starts the GUI. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.getInstance();
        System.out.println("StudyMuse starting (provider: " + config.getProviderName()
                + ", data folder: " + config.getDataDir().toAbsolutePath() + ")");
        if (!config.isEnvFileFound()) {
            System.out.println("No .env file found - copy .env.example to .env and add your API key.");
        }
    }
}
