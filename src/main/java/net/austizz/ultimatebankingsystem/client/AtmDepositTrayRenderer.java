package net.austizz.ultimatebankingsystem.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

final class AtmDepositTrayRenderer {
    private static final String[] NAMES = {"hundred", "fifty", "twenty", "ten", "five", "two", "one"};
    private AtmDepositTrayRenderer() {}
    private static int color(double alpha, int rgb) { return (int)(Math.clamp(alpha, 0, 1) * 255) << 24 | rgb; }
    private static void note(GuiGraphics g, int denomination, int x, int y, int width, double alpha, boolean dim) {
        g.flush(); float tint = dim ? .52F : 1;
        RenderSystem.setShaderColor(tint, tint, tint, (float) alpha);
        var id = ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "textures/item/" + NAMES[denomination] + "_dollar_american.png");
        g.blit(id, x, y, width, Math.max(1, width * 436 / 1024), 0, 294, 1024, 436, 1024, 1024);
        g.flush(); RenderSystem.setShaderColor(1,1,1,1);
    }
    static void render(GuiGraphics g, AtmDepositTray tray, int width, int height, int mouseX, int mouseY, boolean enabled, boolean overFeeder) {
        if (tray.opacity < .015) return;
        var font = Minecraft.getInstance().font; double a = tray.opacity;
        int top = AtmDepositTray.top(height), row = AtmDepositTray.rowHeight(height);
        g.pose().pushPose(); g.pose().translate(0,0,250);
        g.fill(7, top - 13, 100, top + row * 7 + 3, color(a * .74, 0x18251F));
        g.drawString(font, "YOUR BILLS", 13, top - 10, color(a, 0xD4DDD6), false);
        for (int i = 0; i < 7; i++) {
            int y = top + i * row, left = Math.max(0, tray.available[i] - tray.dragging[i]);
            boolean hover = AtmDepositTray.hit(mouseX, mouseY, width, height) == i && enabled;
            if (hover) g.fill(9,y,98,y+row-1,color(a*.7,0x405447));
            note(g,i,12,y+Math.max(1,(row-21)/2),48,a,left==0 || !enabled);
            g.drawString(font, "$" + net.austizz.ultimatebankingsystem.item.BillDenominations.DOLLARS_DESC[i], 64,y+2,color(a,left==0 ? 0xA2AAA4 : 0xF0F4F0),false);
            String quantity = "x" + left;
            float scale = Math.min(1, 32F / Math.max(1,font.width(quantity)));
            g.pose().pushPose();g.pose().translate(64,y+12,0);g.pose().scale(scale,scale,1);
            g.drawString(font,quantity,0,0,color(a,left==0 ? 0x8B968F : 0xC4D6C9),false);g.pose().popPose();
        }
        String hint = tray.message.isEmpty() ? tray.count()>0 ? "Drag to feeder  |  Esc: cancel drag" : "Left: +1 bill  |  Right: up to 64" : tray.message;
        int hintWidth = Math.max(1,width-116);float scale = Math.min(1,hintWidth/(float)Math.max(1,font.width(hint)));
        g.pose().pushPose();g.pose().translate(108,height-20,0);g.pose().scale(scale,scale,1);
        g.drawString(font,hint,0,0,color(a,tray.message.isEmpty()?0xE0EAE2:0xF0C0A3),true);g.pose().popPose();
        if (tray.count()>0) {
            int layer = 0;
            for (int i=6;i>=0;i--) if (tray.dragging[i]>0) { note(g,i,mouseX+9+layer*3,mouseY-6-layer*3,56,a,false);layer++; }
            String count = tray.pending ? "Checking..." : overFeeder ? "Release to insert" : tray.count()+" bills";
            g.drawString(font,count,mouseX+12,mouseY+23,color(a,overFeeder ? 0xB9F0BE : 0xFFFFFF),true);
        }
        g.pose().popPose();
    }
}
