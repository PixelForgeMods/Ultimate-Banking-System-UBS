package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

enum PcFilesIcon {
    BACK("arrow-left"),
    FORWARD("arrow-right"),
    UP("arrow-up"),
    NEW("circle-plus"),
    ADD("plus"),
    CUT("scissors"),
    COPY("copy"),
    PASTE("clipboard-paste"),
    RENAME("text-cursor-input"),
    DELETE("trash-2"),
    SORT("arrow-down-up"),
    VIEW("list"),
    MORE("ellipsis"),
    DETAILS("panel-right"),
    PREVIEW("scan-eye"),
    LEFT_PANE("panel-left"),
    CLOSE("x"),
    MINIMIZE("minus"),
    MAXIMIZE("square"),
    RESTORE_WINDOW("copy"),
    SEARCH("search"),
    DOWN("chevron-down"),
    LEFT("chevron-left"),
    RIGHT("chevron-right"),
    HOME("house"),
    DRIVE("hard-drive"),
    FOLDER("folder", true),
    FOLDER_OPEN("folder-open"),
    DESKTOP("monitor", true),
    DOWNLOADS("download", true),
    DOCUMENTS("files", true),
    PICTURES("image", true),
    MUSIC("music", true),
    VIDEOS("video", true),
    PIN("pin"),
    UNPIN("pin-off"),
    RECENT("clock"),
    STAR("star"),
    STAR_OFF("star-off"),
    EXTRA_LARGE("maximize"),
    LARGE("square"),
    MEDIUM("square-dashed"),
    SMALL("grid-2x2"),
    LIST("columns-2"),
    TABLE("table-2"),
    TILES("layout-grid"),
    CONTENT("list-collapse"),
    FILE_TEXT("file-text"),
    EXTENSION("file-type"),
    COMPACT("list-minus"),
    CALENDAR("calendar"),
    SORT_UP("arrow-up-narrow-wide"),
    SORT_DOWN("arrow-down-wide-narrow"),
    GROUP("list-tree"),
    RESET("rotate-ccw"),
    PATH("route"),
    CLEAR("circle-x"),
    INFO("info"),
    OPEN("external-link"),
    PLAY("play"),
    ROTATE_RIGHT("rotate-cw"),
    LINK("link"),
    CLEAR_FORMAT("remove-formatting"),
    BELL("bell"),
    BELL_OFF("bell-off");

    static final int TILE = 128;
    static final int COLUMNS = 8;
    static final int ROWS = Math.ceilDiv(values().length, COLUMNS);
    final String source;
    final boolean large;

    PcFilesIcon(String source) {
        this(source, false);
    }

    PcFilesIcon(String source, boolean large) {
        this.source = source;
        this.large = large;
    }

    void draw(GuiGraphics graphics, int x, int y, int size, int color) {
        int u = (ordinal() % COLUMNS) * TILE;
        int v = (ordinal() / COLUMNS) * TILE;
        RenderSystem.enableBlend();
        graphics.setColor(((color >>> 16) & 255) / 255f, ((color >>> 8) & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
        if (large && size * Minecraft.getInstance().getWindow().getGuiScale() > TILE) {
            int pixels = PcDesktopScreen.HIGH_RES_FILE_ICON_PIXELS;
            graphics.blit(Textures.LARGE[ordinal()], x, y, size, size, 0f, 0f, pixels, pixels, pixels, pixels);
        } else {
            graphics.blit(Textures.ATLAS, x, y, size, size, u, v, TILE, TILE, COLUMNS * TILE, ROWS * TILE);
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static final class Textures {
        private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
                "ultimatebankingsystem", "textures/gui/pc/files_icons.png");
        private static final ResourceLocation[] LARGE = new ResourceLocation[values().length];

        static {
            for (var icon : values()) {
                if (icon.large) {
                    LARGE[icon.ordinal()] = ResourceLocation.fromNamespaceAndPath(
                            "ultimatebankingsystem", "textures/gui/pc/files_icons/" + icon.source + ".png");
                }
            }
        }
    }
}
