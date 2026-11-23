package dev.halo.render;

import dev.halo.module.ModuleManager;
import dev.halo.module.Modules;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

/** Client-side wings drawn on the player's back, visible in third person. */
public class WingFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    // Reuses the same blank white texture as hats; the module's color tints it.
    private static final Identifier TEX = Identifier.of("halo", "textures/entity/hat.png");
    private static final ModelPart[] PARTS = new ModelPart[5];

    public WingFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) { super(context); }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (type == EntityType.PLAYER) helper.register(new WingFeature((FeatureRendererContext) renderer));
        });
    }

    // One side (+X); the other side is mirrored when rendering. Root sits at the shoulder blade.
    private static ModelPart buildSide(int type) {
        ModelPartBuilder b = ModelPartBuilder.create().uv(0, 0);
        switch (type) {
            case 0 -> { // angel: layered flat feather-like plates
                b.cuboid(0f, -2f, -0.5f, 10, 10, 1);
                b.cuboid(8f, 0f, -0.5f, 6, 6, 1);
            }
            case 1 -> { // bat: thin ribbed membrane
                b.cuboid(0f, -1f, -0.5f, 9, 2, 1);
                b.cuboid(6f, 2f, -0.5f, 7, 2, 1);
                b.cuboid(4f, 5f, -0.5f, 9, 1, 1);
            }
            case 2 -> { // dragon: a bold single spar + membrane
                b.cuboid(0f, -2f, -0.5f, 12, 1, 1);
                b.cuboid(9f, -1f, -0.5f, 5, 7, 1);
            }
            case 3 -> { // fairy: small, thin and short
                b.cuboid(0f, 0f, -0.3f, 6, 6, 1);
            }
            default -> { // mechanical: blocky segmented panels
                b.cuboid(0f, -2f, -1f, 5, 5, 2);
                b.cuboid(5f, 0f, -1f, 5, 5, 2);
                b.cuboid(10f, 2f, -1f, 4, 4, 2);
            }
        }
        ModelData md = new ModelData();
        md.getRoot().addChild("wing", b, ModelTransform.NONE);
        return TexturedModelData.of(md, 64, 64).createModel();
    }

    @Override
    public void render(MatrixStack m, OrderedRenderCommandQueue q, int light, PlayerEntityRenderState s, float yaw, float pitch) {
        Modules.Wings wings = ModuleManager.WINGS;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (wings == null || !wings.enabled || mc.player == null || s.id != mc.player.getId()) return;

        int t = wings.type.index;
        if (PARTS[t] == null) PARTS[t] = buildSide(t);
        int tint = 0xFF000000 | wings.color.rgb;
        float sz = wings.size.f();
        float flap = (float) Math.sin(System.nanoTime() / 4.0e8) * 8f;

        for (int side = -1; side <= 1; side += 2) {
            m.push();
            m.translate(0f, -1.3f, 0.3f);
            m.scale(sz, sz, sz);
            m.multiply(new Quaternionf().rotationZYX((float) Math.toRadians(side * (20f + flap)), 0f, 0f));
            if (side < 0) m.scale(-1f, 1f, 1f);
            q.submitModelPart(PARTS[t], m, RenderLayers.entityCutoutNoCull(TEX), light, OverlayTexture.DEFAULT_UV,
                    null, tint, null);
            m.pop();
        }
    }
}
