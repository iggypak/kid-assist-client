package ru.kidassistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Metadata-only logs: never record children's questions or answers. */
public final class ClientLogging {
    private ClientLogging() {
    }

    public static Logger create() {
        Logger logger = Logger.getLogger("ru.kidassistant.client");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.INFO);
        try {
            Path folder = Path.of(System.getProperty("user.home"), ".local", "state", "kid-assistant");
            Files.createDirectories(folder);
            FileHandler handler = new FileHandler(folder.resolve("client-%g.log").toString(),
                    1_000_000, 3, true);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException exception) {
            // Logging failure must not prevent the child from using the application.
            System.err.println("Не удалось открыть технический лог: " + exception.getClass().getSimpleName());
        }
        return logger;
    }
}
