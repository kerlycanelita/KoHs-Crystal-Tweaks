package com.zymekoh.crystaltweaks.client.hub;

/**
 * Easing and timing for the settings hub. Every animation here is driven by elapsed real time, so
 * it plays at the same speed at 30 and at 500 frames per second.
 */
public final class HubMotion {
    private HubMotion() {
    }

    public static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }

    /** How far {@code elapsed} is into {@code duration}, from 0 to 1. */
    public static float progress(long elapsedNanos, long durationNanos) {
        if (durationNanos <= 0L) {
            return 1.0F;
        }
        return clamp01(elapsedNanos / (float) durationNanos);
    }

    /** {@link #progress} for something that starts {@code delay} after {@code since}. */
    public static float progress(long now, long since, long delayNanos, long durationNanos) {
        return progress(now - since - delayNanos, durationNanos);
    }

    public static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp01(value);
        return 1.0F - inverse * inverse * inverse;
    }

    public static float easeInCubic(float value) {
        float t = clamp01(value);
        return t * t * t;
    }

    public static float easeInOutCubic(float value) {
        float t = clamp01(value);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0D) / 2.0F;
    }

    public static float easeOutQuart(float value) {
        float inverse = 1.0F - clamp01(value);
        return 1.0F - inverse * inverse * inverse * inverse;
    }

    public static float easeInOutSine(float value) {
        return (float) (-(Math.cos(Math.PI * clamp01(value)) - 1.0D) / 2.0D);
    }

    /** A restrained overshoot: heavy and sharp, never the bounce of a mobile app. */
    public static float easeOutBack(float value) {
        float t = clamp01(value) - 1.0F;
        float overshoot = 1.25F;
        return 1.0F + (overshoot + 1.0F) * t * t * t + overshoot * t * t;
    }

    public static float smoothstep(float edge0, float edge1, float value) {
        float t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Moves {@code current} toward {@code target} the way a damped spring settles, the same amount
     * per millisecond whatever the frame rate. {@code tauMillis} is the time to cover ~63% of it.
     */
    public static float damp(float current, float target, float frameMillis, float tauMillis) {
        float response = 1.0F - (float) Math.exp(-frameMillis / Math.max(1.0F, tauMillis));
        return current + (target - current) * response;
    }

    /** A breathing value between 0 and 1 with the given period in seconds. */
    public static float breathe(double seconds, double period) {
        return 0.5F + 0.5F * (float) Math.sin(seconds * Math.PI * 2.0D / period);
    }

    /** A fixed pseudo-random value in [0, 1) for {@code seed}: no Random shared between frames. */
    public static float hash(long seed) {
        long value = seed * 6364136223846793005L + 1442695040888963407L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return (value >>> 40) / (float) (1 << 24);
    }

    public static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    public static int lerp(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }
}
