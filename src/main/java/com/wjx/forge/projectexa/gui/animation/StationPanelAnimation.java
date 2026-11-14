package com.wjx.forge.projectexa.gui.animation;

/** Reversible drawer motion and size changes for station panels. */
public final class StationPanelAnimation {
    public static final long DURATION_MS = 180;
    private int station;
    private int target;
    private float progress;
    private float resizeProgress;
    private float craftingFrom;
    private long lastUpdate;

    public StationPanelAnimation(int station, long now) {
        reset(station, now);
    }

    public void reset(int value, long now) {
        station = target = value;
        progress = value < 0 ? 0 : 1;
        resizeProgress = 1;
        craftingFrom = value == 0 ? 1 : 0;
        lastUpdate = now;
    }

    public void select(int value, long now) {
        update(now);
        target = target == value ? -1 : value;
        if (target >= 0 && station != target) {
            // Switching replaces the open contents directly. Only reselecting
            // the active station targets the collapsed state.
            craftingFrom = craftingWeight();
            float next = target == 0 ? 1 : 0;
            resizeProgress = station < 0 || craftingFrom == next ? 1 : 0;
            station = target;
        }
        update(now);
    }

    public void update(long now) {
        float remaining = Math.max(0, now - lastUpdate) / (float) DURATION_MS;
        lastUpdate = now;
        resizeProgress = Math.min(1, resizeProgress + remaining);
        if (target < 0) {
            progress = Math.max(0, progress - remaining);
            if (progress <= 0) {
                station = -1;
                resizeProgress = 1;
            }
        } else progress = Math.min(1, progress + remaining);
    }

    public int station() { return station; }
    public int target() { return target; }
    public boolean isResizing() { return resizeProgress < 1; }
    public float craftingWeight() {
        float next = station == 0 ? 1 : 0;
        return craftingFrom + (next - craftingFrom) * ease(resizeProgress);
    }
    public static float ease(float value) { return value * value * (3 - 2 * value); }
    public float openness() { return ease(progress); }
    public boolean isMoving() { return station != target || station >= 0 && (progress < 1 || isResizing()); }
}
