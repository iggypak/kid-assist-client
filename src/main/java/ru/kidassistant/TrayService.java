package ru.kidassistant;

import java.awt.AWTError;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class TrayService implements AutoCloseable {
    private final Executor callbackExecutor;
    private volatile boolean started;
    private volatile boolean closed;
    private SystemTray tray;
    private TrayIcon icon;
    private PropertyChangeListener availabilityListener;

    public TrayService(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    public CompletableFuture<Boolean> install(Runnable show, Runnable hide, Runnable exit, Runnable unavailable) {
        started = true;
        try {
            return CompletableFuture.supplyAsync(() -> addIcon(show, hide, exit, unavailable), EventQueue::invokeLater);
        } catch (RuntimeException | AWTError | LinkageError error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    private boolean addIcon(Runnable show, Runnable hide, Runnable exit, Runnable unavailable) {
        if (closed || !SystemTray.isSupported()) {
            return false;
        }
        if (icon != null) {
            return true;
        }

        SystemTray systemTray = SystemTray.getSystemTray();
        PopupMenu menu = new PopupMenu();
        menu.add(menuItem("Показать", show));
        menu.add(menuItem("Скрыть", hide));
        menu.addSeparator();
        menu.add(menuItem("Выйти", exit));

        TrayIcon trayIcon = new TrayIcon(createImage(), "Помощник", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(event -> callbackExecutor.execute(show));
        try {
            systemTray.add(trayIcon);
        } catch (AWTException exception) {
            throw new CompletionException(exception);
        }

        tray = systemTray;
        icon = trayIcon;
        availabilityListener = event -> {
            if (event.getNewValue() == null) {
                EventQueue.invokeLater(() -> {
                    if (!closed && icon != null) {
                        removeIcon();
                        callbackExecutor.execute(unavailable);
                    }
                });
            }
        };
        tray.addPropertyChangeListener("systemTray", availabilityListener);

        if (closed) {
            removeIcon();
            return false;
        }
        return true;
    }

    private MenuItem menuItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.addActionListener(event -> callbackExecutor.execute(action));
        return item;
    }

    private static BufferedImage createImage() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(75, 134, 234));
            graphics.fillRoundRect(1, 1, 30, 30, 10, 10);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(6, 7, 20, 15, 6, 6);
            graphics.fillPolygon(new int[]{10, 10, 16}, new int[]{20, 27, 20}, 3);
            graphics.setColor(new Color(75, 134, 234));
            graphics.fillOval(10, 13, 3, 3);
            graphics.fillOval(15, 13, 3, 3);
            graphics.fillOval(20, 13, 3, 3);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Override
    public void close() {
        closed = true;
        if (started) {
            EventQueue.invokeLater(this::removeIcon);
        }
    }

    private void removeIcon() {
        if (tray != null) {
            tray.removePropertyChangeListener("systemTray", availabilityListener);
            tray.remove(icon);
            tray = null;
            icon = null;
            availabilityListener = null;
        }
    }
}
