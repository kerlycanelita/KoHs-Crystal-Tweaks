package com.zymekoh.crystaltweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;

/**
 * The crystal model's own boxes, read once from the model the game draws: each face's corner, its
 * edges along its texture, its normal and where its texels are. The light is built from these, so
 * it sits on the model whatever a resource pack or another mod does to its shape.
 */
final class CrystalShape {
    /**
     * One face of a box, in the box's own units.
     *
     * @param ox the corner where the face's texture starts, and its two neighbours along u and v
     * @param flipped whether going round the corners in texture order turns the wrong way seen from outside
     */
    record Face(float ox, float oy, float oz, float ux, float uy, float uz, float vx, float vy, float vz,
            float nx, float ny, float nz, float u0, float v0, float u1, float v1, boolean flipped) {
    }

    private static CrystalShape shape;
    private static EndCrystalModel source;

    /** The two frames share one box; the core has its own. Six faces each, in the model's order. */
    final Face[] glass;
    final Face[] core;
    final float innerScale;
    final float coreScale;

    private CrystalShape(EndCrystalModel model) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        // The outer frame holds the inner one, which holds the core: the first box and the last.
        model.outerGlass.visit(new PoseStack(), (pose, path, index, cube) -> cubes.add(cube));
        this.glass = faces(cubes.get(0));
        this.core = faces(cubes.get(cubes.size() - 1));
        this.innerScale = model.innerGlass.getInitialPose().xScale();
        this.coreScale = model.cube.getInitialPose().xScale();
    }

    /** The shape of {@code model}, read the first time it is asked for. */
    static CrystalShape of(EndCrystalModel model) {
        if (shape == null || source != model) {
            shape = new CrystalShape(model);
            source = model;
        }
        return shape;
    }

    private static Face[] faces(ModelPart.Cube cube) {
        Face[] faces = new Face[cube.polygons.length];
        for (int index = 0; index < faces.length; index++) {
            ModelPart.Polygon polygon = cube.polygons[index];
            ModelPart.Vertex[] corners = polygon.vertices();
            float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
            for (ModelPart.Vertex corner : corners) {
                u0 = Math.min(u0, corner.u());
                v0 = Math.min(v0, corner.v());
                u1 = Math.max(u1, corner.u());
                v1 = Math.max(v1, corner.v());
            }
            ModelPart.Vertex origin = nearest(corners, u0, v0);
            ModelPart.Vertex alongU = nearest(corners, u1, v0);
            ModelPart.Vertex alongV = nearest(corners, u0, v1);
            float ux = alongU.worldX() - origin.worldX(), uy = alongU.worldY() - origin.worldY(), uz = alongU.worldZ() - origin.worldZ();
            float vx = alongV.worldX() - origin.worldX(), vy = alongV.worldY() - origin.worldY(), vz = alongV.worldZ() - origin.worldZ();
            float nx = polygon.normal().x(), ny = polygon.normal().y(), nz = polygon.normal().z();
            // u cross v points out of the face when the texture is laid the usual way round.
            float out = (uy * vz - uz * vy) * nx + (uz * vx - ux * vz) * ny + (ux * vy - uy * vx) * nz;
            faces[index] = new Face(origin.worldX(), origin.worldY(), origin.worldZ(), ux, uy, uz, vx, vy, vz,
                    nx, ny, nz, u0, v0, u1, v1, out < 0.0F);
        }
        return faces;
    }

    private static ModelPart.Vertex nearest(ModelPart.Vertex[] corners, float u, float v) {
        ModelPart.Vertex best = corners[0];
        float least = Float.MAX_VALUE;
        for (ModelPart.Vertex corner : corners) {
            float distance = Math.abs(corner.u() - u) + Math.abs(corner.v() - v);
            if (distance < least) {
                least = distance;
                best = corner;
            }
        }
        return best;
    }
}
