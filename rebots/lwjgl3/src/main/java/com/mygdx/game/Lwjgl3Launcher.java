package com.mygdx.game;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        new Lwjgl3Application(new Joc(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Exemple Viewport libGDX");
        config.setWindowedMode(800, 500); // 8x5 aspect ratio
        config.useVsync(true);
        config.setForegroundFPS(60);
        return config;
    }
}
