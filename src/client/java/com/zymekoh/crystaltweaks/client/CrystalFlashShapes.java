package com.zymekoh.crystaltweaks.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Silhouettes for the death flash, drawn in the same camera-facing plane and the same additive
 * material as the original burst.
 *
 * <p>Everything here is geometry submitted for one frame. Only the lightning style looks at other
 * players, and only at the ones the player could see when the crystal exploded: not invisible, not
 * spectating, in front of the camera and with nothing solid in between. Their positions are taken
 * once, at the explosion, and every bolt has the same length, so a bolt never points at or measures
 * the distance to anyone the player could not already see.</p>
 */
public final class CrystalFlashShapes {
    /**
     * Mask rows are drawn top to bottom. {@code #} is lit, {@code +} is a dim facet, {@code *} burns
     * in the hot colour and {@code .} is a hole the additive pass skips.
     */
    private static final String[] SKULL = {
            "....######....",
            "..##########..",
            ".############.",
            "##############",
            "##############",
            "##...####...##",
            "#....####....#",
            "##...####...##",
            "##############",
            "######..######",
            ".#####..#####.",
            "..##########..",
            "...##.##.##...",
    };

    private static final String[] HEART = {
            ".###.....###.",
            "#*###...#####",
            "#*####.######",
            "#############",
            "#############",
            "#############",
            ".###########.",
            "..#########..",
            "...#######...",
            "....#####....",
            ".....###.....",
            "......#......",
    };

    private static final String[] CROWN = {
            "*.....*.....*",
            "#.....#.....#",
            "##...###...##",
            "###.#####.###",
            "#############",
            "#############",
            "##*##+#+##*##",
            "#############",
            ".###########.",
    };

    private static final String[] GEM = {
            "....#######....",
            "...#+#####+#...",
            "..#++#####++#..",
            ".#############.",
            "###############",
            ".#+#########+#.",
            "..#+#######+#..",
            "...#+#####+#...",
            "....#+###+#....",
            ".....#+#+#.....",
            "......#*#......",
            ".......#.......",
    };

    private static final String[] CRESCENT = crescentMask(19);

    /** Bolts thrown per flash. Past this the screen reads as noise rather than as lightning. */
    private static final int MAX_BOLTS = 5;

    /** Players further than this from the blast contribute no bolt. */
    private static final double BOLT_RANGE = 24.0D;

    /** Every bolt reaches this far, in blast radii, whoever it points at. */
    private static final float BOLT_REACH = 1.5F;

    /** Full block light, so the apparition is as bright in a cave as it is at noon. */
    private static final int FULL_BRIGHT = 15728880;

    /** No damage overlay on the head. */
    private static final int NO_OVERLAY = 655360;

    private CrystalFlashShapes() { }

    /**
     * Texture of the local player's own skin. From the title screen, where there is no player yet,
     * the settings preview falls back to this account's default skin rather than showing nothing.
     */
    public static Identifier playerSkin() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                return ((AbstractClientPlayer) player).getSkin().body().texturePath();
            }
            return DefaultPlayerSkin.get(minecraft.getUser().getProfileId()).body().texturePath();
        } catch (RuntimeException skinNotReady) {
            return null;
        }
    }

    /** The flash colour washed toward white, which is what makes the head read as a ghost. */
    public static int paleTint(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        return 0xFF000000
                | Math.round(r + (255 - r) * 0.62F) << 16
                | Math.round(g + (255 - g) * 0.62F) << 8
                | Math.round(b + (255 - b) * 0.62F);
    }

    /**
     * Draws the player's own head as a pale apparition: the face from their skin, tinted with a
     * washed-out glow colour and see-through, plus the hat layer a little larger in front of it.
     *
     * <p>The skin is the one the client is already rendering on the player. Nothing is downloaded,
     * and no other player's skin is read.</p>
     */
    public static void submitPlayerHead(
            CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius, float gain) {
        float half = radius * 0.95F;
        // A skin is 64 wide: the face sits at x 8-16, y 8-16, and the hat layer at x 40-48.
        face(buffer, matrix, color, half, gain * 0.7F, 0.125F, 0.125F, 0.25F, 0.25F);
        face(buffer, matrix, color, half * 1.12F, gain * 0.38F, 0.625F, 0.125F, 0.75F, 0.25F);
    }

    /**
     * A white veil laid over the face in the additive material. It lifts every colour of the skin
     * toward white together, which a multiplied tint cannot do, and that is what makes it pale.
     */
    public static void submitHeadVeil(
            CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius, float gain) {
        float half = radius * 0.95F;
        quad(buffer, matrix, color, -half, -half, half, half, gain * 0.34F);
    }

    private static void face(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float half,
            float gain, float u0, float v0, float u1, float v1) {
        int alpha = CrystalGlowMath.alpha(gain);
        if (alpha <= 0) {
            return;
        }
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        // Two windings so the settings screen's mirrored pose shows the head too.
        for (int pass = 0; pass < 2; pass++) {
            boolean flipped = pass == 1;
            headVertex(buffer, matrix, r, g, b, alpha, -half, -half, flipped ? u1 : u0, v1);
            headVertex(buffer, matrix, r, g, b, alpha, half, -half, flipped ? u0 : u1, v1);
            headVertex(buffer, matrix, r, g, b, alpha, half, half, flipped ? u0 : u1, v0);
            headVertex(buffer, matrix, r, g, b, alpha, -half, half, flipped ? u1 : u0, v0);
        }
    }

    private static void headVertex(CrystalGlowBuffer buffer, Matrix4f matrix,
            int r, int g, int b, int alpha, float x, float y, float u, float v) {
        buffer.texturedVertex(matrix, r, g, b, alpha, x, y, u, v, NO_OVERLAY, FULL_BRIGHT);
    }

    /**
     * The players a lightning flash may point at, captured once when the crystal explodes.
     *
     * <p>Only players this client could see at that moment qualify: not the player, not a spectator,
     * not invisible to the player, inside the view cone and with a clear line of sight from the
     * player's eyes to their head or body. Anyone else is skipped, so a bolt adds nothing to what
     * was already on screen.</p>
     */
    public static List<Vec3> visibleBoltTargets(Vec3 origin) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer self = minecraft.player;
        if (origin == null || self == null || minecraft.level == null) {
            return List.of();
        }
        Vec3 eye = self.getEyePosition();
        Vec3 look = self.getViewVector(1.0F);
        double cone = Math.cos(Math.toRadians(viewHalfAngle(minecraft)));
        List<Vec3> targets = new ArrayList<>();
        for (Player player : minecraft.level.players()) {
            if (targets.size() >= MAX_BOLTS) {
                break;
            }
            if (player == self || player.isSpectator() || player.isInvisibleTo(self)) {
                continue;
            }
            Vec3 body = player.position().add(0, player.getBbHeight() / 2, 0);
            double distance = body.distanceTo(origin);
            if (distance < 0.5D || distance > BOLT_RANGE) {
                continue;
            }
            Vec3 head = player.getEyePosition();
            boolean seen = (inView(eye, look, cone, head) && clearSight(minecraft, self, eye, head))
                    || (inView(eye, look, cone, body) && clearSight(minecraft, self, eye, body));
            if (seen) {
                targets.add(body);
            }
        }
        return List.copyOf(targets);
    }

    /** Half the horizontal field of view for a 16:9 window, from the player's own FOV setting. */
    private static double viewHalfAngle(Minecraft minecraft) {
        double vertical = Math.toRadians(minecraft.options.fov().get());
        double horizontal = Math.atan(Math.tan(vertical / 2.0D) * 16.0D / 9.0D);
        return Math.min(80.0D, Math.toDegrees(horizontal));
    }

    private static boolean inView(Vec3 eye, Vec3 look, double cone, Vec3 target) {
        Vec3 toTarget = target.subtract(eye);
        double length = toTarget.length();
        return length > 0.0001D && toTarget.dot(look) / length >= cone;
    }

    private static boolean clearSight(Minecraft minecraft, LocalPlayer self, Vec3 eye, Vec3 target) {
        return minecraft.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE, self)).getType() == HitResult.Type.MISS;
    }

    /**
     * @param origin   world position of the blast, or {@code null} for the settings-screen preview
     * @param targets  players the lightning style may point at, from {@link #visibleBoltTargets}
     * @param progress how far the flash has faded, 0 at the explosion and 1 when it is gone
     */
    public static void submit(
            CrystalFlashStyle style,
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            Quaternionf cameraOrientation,
            int color,
            int hotColor,
            float radius,
            float gain,
            Vec3 origin,
            List<Vec3> targets,
            float progress
    ) {
        switch (style) {
            case SKULL -> mask(buffer, matrix, SKULL, color, hotColor, radius * 1.15F, gain);
            case LIGHTNING -> bolts(buffer, matrix, cameraOrientation, color, hotColor, radius, gain,
                    origin, targets);
            case HEART -> mask(buffer, matrix, HEART, color, hotColor, radius * 1.1F, gain);
            case STAR -> star(buffer, matrix, color, hotColor, radius, gain, progress);
            case SHOCKWAVE -> shockwave(buffer, matrix, color, hotColor, radius, gain, progress);
            case VORTEX -> vortex(buffer, matrix, color, hotColor, radius, gain, progress);
            case CROWN -> mask(buffer, matrix, CROWN, color, hotColor, radius * 1.1F, gain);
            case CRESCENT -> mask(buffer, matrix, CRESCENT, color, hotColor, radius * 1.1F, gain);
            case SNOWFLAKE -> snowflake(buffer, matrix, color, hotColor, radius, gain, progress);
            case FLOWER -> flower(buffer, matrix, color, hotColor, radius, gain, progress);
            case GEM -> mask(buffer, matrix, GEM, color, hotColor, radius * 1.15F, gain);
            case SWORDS -> swords(buffer, matrix, color, hotColor, radius, gain);
            default -> { }
        }
    }

    /**
     * Draws a mask as one quad per lit cell, brightest at the middle so the shape still reads as a
     * burst of light rather than as a flat sticker pasted over the world.
     */
    private static void mask(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            String[] rows,
            int color,
            int hotColor,
            float radius,
            float gain
    ) {
        int height = rows.length;
        int width = rows[0].length();
        float cell = radius * 2F / Math.max(width, height);
        float originX = -cell * width / 2F;
        float originY = cell * height / 2F;
        for (int row = 0; row < height; row++) {
            String line = rows[row];
            for (int column = 0; column < width && column < line.length(); column++) {
                char kind = line.charAt(column);
                if (kind == '.') {
                    continue;
                }
                float x0 = originX + cell * column;
                float y0 = originY - cell * (row + 1);
                // Cells near the middle burn hotter, which keeps the silhouette from looking flat.
                float dx = (column + 0.5F) / width - 0.5F;
                float dy = (row + 0.5F) / height - 0.5F;
                float centre = Math.max(0F, 1F - (float) Math.sqrt(dx * dx + dy * dy) * 1.7F);
                if (kind == '+') {
                    quad(buffer, matrix, color, x0, y0, x0 + cell, y0 + cell, gain * 0.32F);
                    continue;
                }
                if (kind == '*') {
                    quad(buffer, matrix, hotColor, x0, y0, x0 + cell, y0 + cell, gain);
                    continue;
                }
                quad(buffer, matrix, color, x0, y0, x0 + cell, y0 + cell, gain * (0.55F + centre * 0.45F));
                if (centre > 0.62F) {
                    quad(buffer, matrix, hotColor,
                            x0 + cell * 0.18F, y0 + cell * 0.18F,
                            x0 + cell * 0.82F, y0 + cell * 0.82F,
                            gain * (centre - 0.62F) * 1.4F);
                }
            }
        }
    }

    /** A crescent moon cut from one disc by another, one star in its hollow and one beyond it. */
    private static String[] crescentMask(int size) {
        String[] rows = new String[size];
        for (int row = 0; row < size; row++) {
            StringBuilder line = new StringBuilder(size);
            for (int column = 0; column < size; column++) {
                float x = (column + 0.5F) / size * 2F - 1F;
                float y = 1F - (row + 0.5F) / size * 2F;
                boolean moon = x * x + y * y <= 0.86F * 0.86F;
                float cx = x - 0.40F, cy = y - 0.20F;
                boolean bite = cx * cx + cy * cy <= 0.72F * 0.72F;
                boolean star = (Math.abs(x - 0.45F) + Math.abs(y - 0.10F) < 0.10F)
                        || (Math.abs(x - 0.80F) + Math.abs(y - 0.62F) < 0.07F);
                line.append(moon && !bite ? (x < -0.6F ? '*' : '#') : star ? '*' : '.');
            }
            rows[row] = line.toString();
        }
        return rows;
    }

    /**
     * Throws a jagged bolt toward each visible player, projected into the camera-facing plane so
     * the bolt reads from any angle. With nobody to point at it throws a fixed ring instead.
     */
    private static void bolts(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            Quaternionf cameraOrientation,
            int color,
            int hotColor,
            float radius,
            float gain,
            Vec3 origin,
            List<Vec3> targets
    ) {
        List<float[]> directions = new ArrayList<>();
        if (origin != null && cameraOrientation != null && targets != null) {
            Quaternionf inverse = new Quaternionf(cameraOrientation).conjugate();
            for (Vec3 target : targets) {
                Vec3 delta = target.subtract(origin);
                Vector3f local = inverse.transform(
                        new Vector3f((float) delta.x, (float) delta.y, (float) delta.z));
                float planar = (float) Math.sqrt(local.x * local.x + local.y * local.y);
                if (planar < 0.0001F) {
                    continue;
                }
                // The same reach for every bolt: it shows a direction, never a distance.
                directions.add(new float[] {local.x / planar, local.y / planar, radius * BOLT_REACH});
            }
        }
        if (directions.isEmpty()) {
            // No one in sight, or the preview: a ring of bolts so the style is never invisible.
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2 * i / 6;
                directions.add(new float[] {
                        (float) Math.cos(angle), (float) Math.sin(angle), radius * 1.4F});
            }
        }

        for (int index = 0; index < directions.size(); index++) {
            float[] bolt = directions.get(index);
            // Deterministic per bolt, so a bolt does not reshuffle its own kinks between frames.
            long seed = index * 2654435761L;
            jagged(buffer, matrix, color, hotColor, bolt[0], bolt[1], bolt[2], gain, seed);
        }
        // A small core keeps the bolts anchored to something instead of starting in empty air.
        disc(buffer, matrix, hotColor, radius * 0.22F, gain * 0.9F);
    }

    /** A five-pointed star turning slowly as it fades, with a hot star inside it. */
    private static void star(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain, float progress) {
        float turn = (float) (-Math.PI / 2.0D) + progress * 0.9F;
        starPolygon(buffer, matrix, color, radius * 1.05F, 0.42F, turn, gain, gain * 0.35F);
        starPolygon(buffer, matrix, hotColor, radius * 0.5F, 0.42F, turn, gain * 0.85F, gain * 0.2F);
    }

    private static void starPolygon(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float outer,
            float innerRatio, float turn, float centreGain, float edgeGain) {
        int points = 10;
        float[] xs = new float[points];
        float[] ys = new float[points];
        for (int i = 0; i < points; i++) {
            float r = i % 2 == 0 ? outer : outer * innerRatio;
            double angle = turn + Math.PI * 2 * i / points;
            xs[i] = (float) Math.cos(angle) * r;
            ys[i] = (float) Math.sin(angle) * r;
        }
        fan(buffer, matrix, color, xs, ys, centreGain, edgeGain);
    }

    /** A ring that runs outward as the flash fades, a second one lagging behind it. */
    private static void shockwave(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain, float progress) {
        float outer = radius * (0.35F + 0.95F * progress);
        ring(buffer, matrix, color, outer, radius * (0.24F - 0.1F * progress), gain);
        float inner = radius * (0.18F + 0.7F * progress);
        ring(buffer, matrix, hotColor, inner, radius * 0.08F, gain * 0.7F);
        disc(buffer, matrix, hotColor, radius * 0.3F, gain * (1F - progress));
    }

    /** Three arms spiralling out of the blast and turning as it fades. */
    private static void vortex(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain, float progress) {
        int points = 16;
        for (int arm = 0; arm < 3; arm++) {
            float[] xs = new float[points];
            float[] ys = new float[points];
            for (int i = 0; i < points; i++) {
                float t = i / (float) (points - 1);
                double angle = arm * Math.PI * 2 / 3 + t * Math.PI * 2.6D + progress * 2.4D;
                float r = radius * (0.12F + 0.95F * t);
                xs[i] = (float) Math.cos(angle) * r;
                ys[i] = (float) Math.sin(angle) * r;
            }
            strip(buffer, matrix, color, xs, ys, radius * 0.11F, gain * 0.8F);
            strip(buffer, matrix, hotColor, xs, ys, radius * 0.04F, gain);
        }
        disc(buffer, matrix, hotColor, radius * 0.2F, gain * 0.9F);
    }

    /** Six arms with two pairs of branches each, turning slightly as the flash fades. */
    private static void snowflake(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain, float progress) {
        float turn = progress * 0.35F;
        for (int arm = 0; arm < 6; arm++) {
            double angle = turn + Math.PI * 2 * arm / 6;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float length = radius * 1.05F;
            line(buffer, matrix, color, dx * radius * 0.1F, dy * radius * 0.1F,
                    dx * length, dy * length, radius * 0.055F, gain, gain * 0.45F);
            for (int branch = 0; branch < 2; branch++) {
                float along = radius * (branch == 0 ? 0.45F : 0.72F);
                float reach = radius * (branch == 0 ? 0.3F : 0.2F);
                float bx = dx * along, by = dy * along;
                for (int side = -1; side <= 1; side += 2) {
                    double branchAngle = angle + side * Math.toRadians(52);
                    line(buffer, matrix, color, bx, by,
                            bx + (float) Math.cos(branchAngle) * reach,
                            by + (float) Math.sin(branchAngle) * reach,
                            radius * 0.04F, gain * 0.85F, gain * 0.35F);
                }
            }
            disc(buffer, matrix, hotColor, radius * 0.06F, gain * 0.6F, dx * length, dy * length);
        }
        disc(buffer, matrix, hotColor, radius * 0.16F, gain);
    }

    /** Six petals around a hot heart, with a smaller ring of petals between them. */
    private static void flower(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain, float progress) {
        float turn = progress * 0.5F;
        for (int petal = 0; petal < 6; petal++) {
            double angle = turn + Math.PI * 2 * petal / 6;
            ellipse(buffer, matrix, color, angle, radius * 0.5F, radius * 0.46F, radius * 0.2F,
                    gain * 0.9F, gain * 0.25F);
        }
        for (int petal = 0; petal < 6; petal++) {
            double angle = turn + Math.PI / 6 + Math.PI * 2 * petal / 6;
            ellipse(buffer, matrix, hotColor, angle, radius * 0.32F, radius * 0.3F, radius * 0.12F,
                    gain * 0.6F, gain * 0.15F);
        }
        disc(buffer, matrix, hotColor, radius * 0.17F, gain);
    }

    /** Two swords crossed at the middle of their blades, hilts down. */
    private static void swords(CrystalGlowBuffer buffer, Matrix4f matrix, int color, int hotColor,
            float radius, float gain) {
        for (int side = -1; side <= 1; side += 2) {
            double angle = side * Math.toRadians(40);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float pivot = radius * 0.3F;
            // Blade, then its bright edge, then the guard, grip and pommel.
            swordPart(buffer, matrix, color, cos, sin, pivot, 0F, radius * 0.3F, radius * 0.07F,
                    radius * 0.62F, gain);
            swordTip(buffer, matrix, color, cos, sin, pivot, radius * 0.92F, radius * 1.08F,
                    radius * 0.07F, gain);
            swordPart(buffer, matrix, hotColor, cos, sin, pivot, 0F, radius * 0.3F, radius * 0.022F,
                    radius * 0.6F, gain * 0.9F);
            swordPart(buffer, matrix, hotColor, cos, sin, pivot, 0F, -radius * 0.3F, radius * 0.26F,
                    radius * 0.05F, gain);
            swordPart(buffer, matrix, color, cos, sin, pivot, 0F, -radius * 0.46F, radius * 0.04F,
                    radius * 0.14F, gain * 0.8F);
            swordPart(buffer, matrix, hotColor, cos, sin, pivot, 0F, -radius * 0.64F, radius * 0.07F,
                    radius * 0.06F, gain);
        }
    }

    /** One axis-aligned rectangle of a sword, rotated into place around the crossing point. */
    private static void swordPart(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float cos,
            float sin, float pivot, float cx, float cy, float halfWidth, float halfHeight, float gain) {
        float[] xs = {cx - halfWidth, cx + halfWidth, cx + halfWidth, cx - halfWidth};
        float[] ys = {cy - halfHeight, cy - halfHeight, cy + halfHeight, cy + halfHeight};
        float[] rx = new float[4];
        float[] ry = new float[4];
        for (int i = 0; i < 4; i++) {
            float y = ys[i] - pivot;
            rx[i] = xs[i] * cos - y * sin;
            ry[i] = xs[i] * sin + y * cos;
        }
        triangle(buffer, matrix, color, rx[0], ry[0], gain, rx[1], ry[1], gain, rx[2], ry[2], gain);
        triangle(buffer, matrix, color, rx[0], ry[0], gain, rx[2], ry[2], gain, rx[3], ry[3], gain);
    }

    private static void swordTip(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float cos,
            float sin, float pivot, float base, float tip, float halfWidth, float gain) {
        float[] xs = {-halfWidth, halfWidth, 0F};
        float[] ys = {base - pivot, base - pivot, tip - pivot};
        float[] rx = new float[3];
        float[] ry = new float[3];
        for (int i = 0; i < 3; i++) {
            rx[i] = xs[i] * cos - ys[i] * sin;
            ry[i] = xs[i] * sin + ys[i] * cos;
        }
        triangle(buffer, matrix, color, rx[0], ry[0], gain, rx[1], ry[1], gain, rx[2], ry[2], gain * 0.6F);
    }

    /**
     * Draws one bolt as a single continuous strip.
     *
     * <p>Each segment used to be its own quad with its own normal, so at every kink the two quads
     * met at different angles and left a notch: the bolt read as a row of separate dashes rather
     * than one line. The joints now share their vertices, and the normal at a joint is the average
     * of the two segments meeting there, which is what closes the gap.</p>
     */
    private static void jagged(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            int color,
            int hotColor,
            float dirX,
            float dirY,
            float length,
            float gain,
            long seed
    ) {
        int segments = 6;
        float normalX = -dirY;
        float normalY = dirX;
        float[] xs = new float[segments + 1];
        float[] ys = new float[segments + 1];
        for (int point = 0; point <= segments; point++) {
            float travel = length * point / segments;
            float sway = (point == 0 || point == segments)
                    ? 0F
                    : (noise(seed + point) - 0.5F) * length * 0.22F;
            xs[point] = dirX * travel + normalX * sway;
            ys[point] = dirY * travel + normalY * sway;
        }
        strip(buffer, matrix, color, xs, ys, length * 0.045F, gain * 0.55F);
        strip(buffer, matrix, hotColor, xs, ys, length * 0.017F, gain);
    }

    /** Emits a tapering strip through the points, mitring the normal at every joint. */
    private static void strip(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            int color,
            float[] xs,
            float[] ys,
            float width,
            float gain
    ) {
        int points = xs.length;
        float[] offsetX = new float[points];
        float[] offsetY = new float[points];
        for (int point = 0; point < points; point++) {
            float inX = point > 0 ? xs[point] - xs[point - 1] : xs[1] - xs[0];
            float inY = point > 0 ? ys[point] - ys[point - 1] : ys[1] - ys[0];
            float outX = point < points - 1 ? xs[point + 1] - xs[point] : inX;
            float outY = point < points - 1 ? ys[point + 1] - ys[point] : inY;
            float nx = normalize(-inY, inX, 0) + normalize(-outY, outX, 0);
            float ny = normalize(-inY, inX, 1) + normalize(-outY, outX, 1);
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            if (len < 0.0001F) {
                nx = -inY;
                ny = inX;
                len = (float) Math.sqrt(nx * nx + ny * ny);
                if (len < 0.0001F) {
                    len = 1F;
                }
            }
            // The strip narrows toward its tip and fades out with it.
            float taper = 1F - (float) point / points * 0.75F;
            float half = width * taper / len;
            offsetX[point] = nx * half;
            offsetY[point] = ny * half;
        }
        for (int segment = 0; segment < points - 1; segment++) {
            float a = gain * (1F - (float) segment / points * 0.8F);
            float b = gain * (1F - (float) (segment + 1) / points * 0.8F);
            float x0 = xs[segment], y0 = ys[segment], x1 = xs[segment + 1], y1 = ys[segment + 1];
            triangle(buffer, matrix, color,
                    x0 + offsetX[segment], y0 + offsetY[segment], a,
                    x0 - offsetX[segment], y0 - offsetY[segment], a,
                    x1 - offsetX[segment + 1], y1 - offsetY[segment + 1], b);
            triangle(buffer, matrix, color,
                    x0 + offsetX[segment], y0 + offsetY[segment], a,
                    x1 - offsetX[segment + 1], y1 - offsetY[segment + 1], b,
                    x1 + offsetX[segment + 1], y1 + offsetY[segment + 1], b);
        }
    }

    /** Component {@code axis} of the unit vector along (x, y); 0 for a degenerate segment. */
    private static float normalize(float x, float y, int axis) {
        float length = (float) Math.sqrt(x * x + y * y);
        if (length < 0.0001F) {
            return 0F;
        }
        return (axis == 0 ? x : y) / length;
    }

    /** A straight bar from one point to another, brightest at its start. */
    private static void line(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float x0, float y0,
            float x1, float y1, float halfWidth, float gain0, float gain1) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.0001F) {
            return;
        }
        float nx = -dy / length * halfWidth, ny = dx / length * halfWidth;
        triangle(buffer, matrix, color, x0 + nx, y0 + ny, gain0, x0 - nx, y0 - ny, gain0,
                x1 - nx, y1 - ny, gain1);
        triangle(buffer, matrix, color, x0 + nx, y0 + ny, gain0, x1 - nx, y1 - ny, gain1,
                x1 + nx, y1 + ny, gain1);
    }

    /** A closed polygon filled from its centre, bright in the middle and dimmer at the rim. */
    private static void fan(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float[] xs, float[] ys,
            float centreGain, float edgeGain) {
        for (int i = 0; i < xs.length; i++) {
            int next = (i + 1) % xs.length;
            triangle(buffer, matrix, color, 0F, 0F, centreGain, xs[i], ys[i], edgeGain,
                    xs[next], ys[next], edgeGain);
        }
    }

    /** A petal: an ellipse whose long axis points away from the centre at {@code angle}. */
    private static void ellipse(CrystalGlowBuffer buffer, Matrix4f matrix, int color, double angle,
            float distance, float along, float across, float centreGain, float edgeGain) {
        int segments = 20;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float cx = cos * distance, cy = sin * distance;
        float[] xs = new float[segments];
        float[] ys = new float[segments];
        for (int i = 0; i < segments; i++) {
            double t = Math.PI * 2 * i / segments;
            float lx = (float) Math.cos(t) * along, ly = (float) Math.sin(t) * across;
            xs[i] = cx + lx * cos - ly * sin;
            ys[i] = cy + lx * sin + ly * cos;
        }
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            triangle(buffer, matrix, color, cx, cy, centreGain, xs[i], ys[i], edgeGain,
                    xs[next], ys[next], edgeGain);
        }
    }

    /** A band of light around the centre, fading to nothing at both edges. */
    private static void ring(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius,
            float thickness, float gain) {
        int segments = 48;
        float inner = Math.max(0F, radius - thickness), outer = radius + thickness;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2 * segment / segments, a1 = Math.PI * 2 * (segment + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            triangle(buffer, matrix, color, c0 * inner, s0 * inner, 0F, c0 * radius, s0 * radius, gain,
                    c1 * radius, s1 * radius, gain);
            triangle(buffer, matrix, color, c0 * inner, s0 * inner, 0F, c1 * radius, s1 * radius, gain,
                    c1 * inner, s1 * inner, 0F);
            triangle(buffer, matrix, color, c0 * radius, s0 * radius, gain, c0 * outer, s0 * outer, 0F,
                    c1 * outer, s1 * outer, 0F);
            triangle(buffer, matrix, color, c0 * radius, s0 * radius, gain, c1 * outer, s1 * outer, 0F,
                    c1 * radius, s1 * radius, gain);
        }
    }

    private static void disc(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius, float gain) {
        disc(buffer, matrix, color, radius, gain, 0F, 0F);
    }

    private static void disc(CrystalGlowBuffer buffer, Matrix4f matrix, int color, float radius, float gain,
            float cx, float cy) {
        int segments = 20;
        for (int segment = 0; segment < segments; segment++) {
            double a0 = Math.PI * 2 * segment / segments;
            double a1 = Math.PI * 2 * (segment + 1) / segments;
            triangle(buffer, matrix, color,
                    cx, cy, gain,
                    cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius, 0F,
                    cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, 0F);
        }
    }

    private static void quad(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            int color,
            float x0,
            float y0,
            float x1,
            float y1,
            float gain
    ) {
        triangle(buffer, matrix, color, x0, y0, gain, x1, y0, gain, x1, y1, gain);
        triangle(buffer, matrix, color, x0, y0, gain, x1, y1, gain, x0, y1, gain);
    }

    /** Cheap deterministic hash in [0,1); no allocation and no shared Random between frames. */
    private static float noise(long seed) {
        long value = seed * 6364136223846793005L + 1442695040888963407L;
        value ^= value >>> 33;
        value *= 0xFF51AFD7ED558CCDL;
        value ^= value >>> 33;
        return (value >>> 40) / (float) (1 << 24);
    }

    private static void triangle(
            CrystalGlowBuffer buffer,
            Matrix4f matrix,
            int color,
            float ax, float ay, float aa,
            float bx, float by, float ba,
            float cx, float cy, float ca
    ) {
        // One face in the world, both for the settings screen's mirrored pose: see CrystalGlowBuffer.
        buffer.triangle(matrix, color, ax, ay, aa, bx, by, ba, cx, cy, ca);
    }
}
