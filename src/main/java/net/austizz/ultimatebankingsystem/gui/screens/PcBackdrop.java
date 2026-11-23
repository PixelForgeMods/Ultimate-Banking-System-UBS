package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostPass;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;

final class PcBackdrop implements AutoCloseable {
    private static final int DOWNSAMPLE = 4;
    private static final int TASKBAR_SIGMA = 10;

    private RenderTarget source;
    private RenderTarget horizontal;
    private RenderTarget taskbar;
    private RenderTarget flyout;
    private PostPass horizontalPass;
    private PostPass taskbarPass;
    private PostPass flyoutPass;
    private boolean unavailable;
    private boolean ready;
    private float guiToU;
    private float guiToV;

    void capture(GuiGraphics graphics, int flyoutSigma) {
        ready = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (unavailable || minecraft.options.getMenuBackgroundBlurriness() == 0) {
            return;
        }

        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.viewWidth < 1 || main.viewHeight < 1) {
            return;
        }
        graphics.flush();
        try {
            prepare(minecraft, Math.max(1, main.viewWidth / DOWNSAMPLE), Math.max(1, main.viewHeight / DOWNSAMPLE));
            float guiScale = (float) minecraft.getWindow().getGuiScale();
            guiToU = guiScale / main.viewWidth;
            guiToV = guiScale / main.viewHeight;

            // Capture desktop pixels before painting any of the shell surfaces.
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, source.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.viewWidth, main.viewHeight, 0, 0, source.width, source.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            blur(TASKBAR_SIGMA, taskbarPass);
            if (flyoutSigma != 0) {
                blur(flyoutSigma, flyoutPass);
            }
            ready = true;
        } catch (IOException | RuntimeException exception) {
            unavailable = true;
            close();
            UltimateBankingSystem.LOGGER.warn("PC Acrylic is unavailable; using solid fills", exception);
        } finally {
            main.bindWrite(true);
        }
    }

    private void prepare(Minecraft minecraft, int width, int height) throws IOException {
        if (source == null) {
            source = target(width, height);
            horizontal = target(width, height);
            taskbar = target(width, height);
            flyout = target(width, height);
            var resources = minecraft.getResourceManager();
            horizontalPass = new PostPass(resources, "ultimatebankingsystem:pc_acrylic", source, horizontal, true);
            taskbarPass = new PostPass(resources, "ultimatebankingsystem:pc_acrylic", horizontal, taskbar, true);
            flyoutPass = new PostPass(resources, "ultimatebankingsystem:pc_acrylic", horizontal, flyout, true);
        } else if (source.width == width && source.height == height) {
            return;
        } else {
            resize(source, width, height);
            resize(horizontal, width, height);
            resize(taskbar, width, height);
            resize(flyout, width, height);
        }
        var projection = new Matrix4f().setOrtho(0, width, 0, height, 0.1f, 1000);
        horizontalPass.setOrthoMatrix(projection);
        taskbarPass.setOrthoMatrix(projection);
        flyoutPass.setOrthoMatrix(projection);
    }

    private static RenderTarget target(int width, int height) {
        var target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        return target;
    }

    private static void resize(RenderTarget target, int width, int height) {
        target.resize(width, height, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
    }

    private void blur(int sigma, PostPass verticalPass) {
        horizontalPass.getEffect().safeGetUniform("SigmaUv").set(guiToU * sigma, 0);
        horizontalPass.process(0);
        verticalPass.getEffect().safeGetUniform("SigmaUv").set(0, guiToV * sigma);
        verticalPass.process(0);
    }

    boolean ready() {
        return ready;
    }

    void draw(GuiGraphics graphics, int x, int y, int w, int h, boolean rounded, boolean useFlyout) {
        graphics.flush();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, (useFlyout ? flyout : taskbar).getColorTextureId());
        var pose = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        if (rounded) {
            quad(buffer, pose, x + 3, y, x + w - 3, y + 1);
            quad(buffer, pose, x + 1, y + 1, x + w - 1, y + 3);
            quad(buffer, pose, x, y + 3, x + w, y + h - 3);
            quad(buffer, pose, x + 1, y + h - 3, x + w - 1, y + h - 1);
            quad(buffer, pose, x + 3, y + h - 1, x + w - 3, y + h);
        } else {
            quad(buffer, pose, x, y, x + w, y + h);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private void quad(BufferBuilder buffer, Matrix4f pose, int left, int top, int right, int bottom) {
        float u0 = (left + pose.m30()) * guiToU;
        float u1 = (right + pose.m30()) * guiToU;
        float v0 = 1 - (top + pose.m31()) * guiToV;
        float v1 = 1 - (bottom + pose.m31()) * guiToV;
        buffer.addVertex(pose, left, top, 0).setUv(u0, v0);
        buffer.addVertex(pose, left, bottom, 0).setUv(u0, v1);
        buffer.addVertex(pose, right, bottom, 0).setUv(u1, v1);
        buffer.addVertex(pose, right, top, 0).setUv(u1, v0);
    }

    @Override
    public void close() {
        ready = false;
        if (horizontalPass != null) horizontalPass.close();
        if (taskbarPass != null) taskbarPass.close();
        if (flyoutPass != null) flyoutPass.close();
        if (source != null) source.destroyBuffers();
        if (horizontal != null) horizontal.destroyBuffers();
        if (taskbar != null) taskbar.destroyBuffers();
        if (flyout != null) flyout.destroyBuffers();
        source = horizontal = taskbar = flyout = null;
        horizontalPass = taskbarPass = flyoutPass = null;
    }
}
