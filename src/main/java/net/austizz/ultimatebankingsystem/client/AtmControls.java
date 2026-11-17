package net.austizz.ultimatebankingsystem.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AtmControls {
    public static final int READER = 24, CASH = 25, RECEIPT = 26, LCD = 27;
    private static Vec3 cashSlot, receiptSlot;
    public static Vec3 cashSlot() { return cashSlot; }
    public static Vec3 receiptSlot() { return receiptSlot; }
    private static AABB readerBounds;
    private static Vec3 readerSlot;
    private static AABB lcdBounds;
    public record Button(int index, boolean keypad, AABB bounds, Vec3 origin, double angle) {
        Vec3 unrotate(Vec3 point) {
            return rotateX(point.subtract(origin), -angle).add(origin);
        }

        public Vec3 depression(double amount) {
            return keypad ? rotateX(new Vec3(0, -.08 * amount, 0), angle)
                    : new Vec3(0, 0, .08 * amount);
        }
    }

    private static List<Button> buttons = List.of();
    private static List<Button> wallButtons = List.of();
    private static Button wallLcd, wallReader;
    private static Vec3 wallReaderSlot, wallCash, wallReceipt;
    public record WallPanel(int cell, AABB bounds) {}
    private static List<WallPanel> wallPanels = List.of();
    public static List<WallPanel> wallPanels() { return wallPanels; }
    public static boolean inWall(BlockPos pos) {
        var level=net.minecraft.client.Minecraft.getInstance().level;
        return level!=null && level.getBlockState(pos).getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock;
    }
    public static List<Button> buttons(BlockPos pos) { return inWall(pos)?wallButtons:buttons; }
    public static Vec3 readerSlot(BlockPos pos) { return inWall(pos)?wallReaderSlot:readerSlot; }
    public static Vec3 cashSlot(BlockPos pos) { return inWall(pos)?wallCash:cashSlot; }
    public static Vec3 receiptSlot(BlockPos pos) { return inWall(pos)?wallReceipt:receiptSlot; }
    public static Button screen(BlockPos pos) { return inWall(pos)?wallLcd:new Button(LCD,false,lcdBounds,Vec3.ZERO,0); }
    public static ResourceLocation model(int index,BlockPos pos) { return inWall(pos)?part("buttons/"+index):model(index); }
    public static ResourceLocation part(String name) { return ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID,"block/atm/in_wall/"+name); }
    private static Button readButton(com.google.gson.JsonObject json) {
        return new Button(json.get("index").getAsInt(),json.get("keypad").getAsBoolean(),
                new AABB(vector(json.getAsJsonArray("from")),vector(json.getAsJsonArray("to"))),
                vector(json.getAsJsonArray("origin")),json.get("angle").getAsDouble());
    }

    private AtmControls() {}

    public static ResourceLocation model(int index) {
        return ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "block/atm/buttons/" + index);
    }

    public static ResourceLocation indicator(int index, boolean lit) {
        return ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID,
                "block/atm/indicators/" + index + (lit ? "_on" : "_off"));
    }

    public static List<Button> buttons() {
        return buttons;
    }

    public static void reload(ResourceManager resources) {
        try(var data=new InputStreamReader(resources.open(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID,"in_wall_atm.json")),StandardCharsets.UTF_8)) {
            var json=JsonParser.parseReader(data).getAsJsonObject();
            var loaded=new ArrayList<Button>();
            for(var entry:json.getAsJsonArray("buttons"))loaded.add(readButton(entry.getAsJsonObject()));
            if(loaded.size()!=16)throw new IllegalStateException("In-wall ATM requires sixteen keys");
            wallButtons=List.copyOf(loaded);wallLcd=readButton(json.getAsJsonObject("lcd"));wallReader=readButton(json.getAsJsonObject("reader"));
            wallReaderSlot=vector(json.getAsJsonArray("readerSlot"));wallCash=vector(json.getAsJsonArray("cash"));wallReceipt=vector(json.getAsJsonArray("receipt"));
            var panels=new ArrayList<WallPanel>();
            for(var entry:json.getAsJsonArray("panels")){var p=entry.getAsJsonObject();panels.add(new WallPanel(p.get("cell").getAsInt(),new AABB(vector(p.getAsJsonArray("from")),vector(p.getAsJsonArray("to")))));}
            wallPanels=List.copyOf(panels);
        } catch(IOException e) { throw new IllegalStateException("Cannot load in-wall ATM layout",e); }
        var id = ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_controls.json");
        try (var reader = new InputStreamReader(resources.open(id), StandardCharsets.UTF_8)) {
            var loaded = new ArrayList<Button>();
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var json = entry.getAsJsonObject();
                loaded.add(new Button(json.get("index").getAsInt(), json.get("keypad").getAsBoolean(),
                        new AABB(vector(json.getAsJsonArray("from")), vector(json.getAsJsonArray("to"))),
                        vector(json.getAsJsonArray("origin")), json.get("angle").getAsDouble()));
            }
            if (loaded.size() != 24) throw new IllegalStateException("ATM requires 24 controls");
            buttons = List.copyOf(loaded);
            var readerId = ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_reader.json");
            try (var data = new InputStreamReader(resources.open(readerId), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(data).getAsJsonObject();
                readerBounds = new AABB(vector(json.getAsJsonArray("from")), vector(json.getAsJsonArray("to")));
                readerSlot = vector(json.getAsJsonArray("slot"));
            }
            try (var data = new InputStreamReader(resources.open(ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_dispenser.json")), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(data).getAsJsonObject();
                cashSlot = vector(json.getAsJsonArray("cash")); receiptSlot = vector(json.getAsJsonArray("receipt"));
            }
            var lcdId = ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "atm_lcd.json");
            try (var data = new InputStreamReader(resources.open(lcdId), StandardCharsets.UTF_8)) {
                var json = JsonParser.parseReader(data).getAsJsonObject();
                lcdBounds = new AABB(vector(json.getAsJsonArray("from")), vector(json.getAsJsonArray("to")));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load ATM controls", exception);
        }
    }

    private static Vec3 vector(JsonArray values) {
        return new Vec3(values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble());
    }

    static Vec3 rotateX(Vec3 point, double degrees) {
        double angle = Math.toRadians(degrees), c = Math.cos(angle), s = Math.sin(angle);
        return new Vec3(point.x, point.y * c - point.z * s, point.y * s + point.z * c);
    }

    public static int rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    static Vec3 local(Vec3 point, BlockPos pos, Direction facing) {
        return point.subtract(pos.getX() + .5, pos.getY(), pos.getZ() + .5)
                .yRot((float) Math.toRadians(rotation(facing))).add(.5, 0, .5).scale(16);
    }

    public static int pick(Vec3 start, Vec3 end, BlockPos pos, Direction facing) {
        if(inWall(pos)) {
            var a=local(start,pos,facing);var b=local(end,pos,facing);
            var controls=new ArrayList<>(wallButtons);controls.add(wallReader);
            int hit=pickLocal(a,b,controls);
            if(hit>=0)return hit;
            if(new AABB(wallCash.add(-3.2,-.7,-3),wallCash.add(3.2,.7,.2)).clip(a,b).isPresent())return CASH;
            if(new AABB(wallReceipt.add(-1.5,-1.5,-2.5),wallReceipt.add(1.5,.5,.2)).clip(a,b).isPresent())return RECEIPT;
            var screenHit=wallLcd.bounds.clip(wallLcd.unrotate(a),wallLcd.unrotate(b));
            if(screenHit.isPresent()) {
                var p=screenHit.get();
                int touch=touchIndex((wallLcd.bounds.maxX-p.x)/wallLcd.bounds.getXsize()*1024,
                        (wallLcd.bounds.maxY-p.y)/wallLcd.bounds.getYsize()*768);
                return touch<0?LCD:touch;
            }
            return -1;
        }
        int result = pickLocal(local(start, pos, facing), local(end, pos, facing), buttons, readerBounds);
        var a = local(start, pos, facing); var b = local(end, pos, facing);
        if (cashSlot != null && new AABB(cashSlot.add(-2.4, -.6, -3), cashSlot.add(2.4, .7, .1)).clip(a, b).isPresent()) return CASH;
        if (receiptSlot != null && new AABB(receiptSlot.add(-1, -1.8, -2.5), receiptSlot.add(1, .5, .1)).clip(a, b).isPresent()) return RECEIPT;
        if (result < 0 && lcdBounds != null && lcdBounds.inflate(.03).clip(a,b).isPresent()) return LCD;
        return result;
    }

    public static Vec3 readerSlot() {
        return readerSlot;
    }

    public static AABB lcdBounds() { return lcdBounds; }

    public static Vec3 world(Vec3 modelPoint, BlockPos pos, Direction facing) {
        return modelPoint.scale(1.0 / 16).subtract(.5, 0, .5)
                .yRot((float) Math.toRadians(-rotation(facing)))
                .add(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }

    static int pickLocal(Vec3 start, Vec3 end, List<Button> controls) {
        return pickLocal(start, end, controls, null);
    }

    static int pickLocal(Vec3 start, Vec3 end, List<Button> controls, AABB reader) {
        double closest = Double.POSITIVE_INFINITY;
        int result = -1;
        for (var button : controls) {
            var rayStart = button.unrotate(start);
            var hit = button.bounds.clip(rayStart, button.unrotate(end));
            if (hit.isPresent() && hit.get().distanceToSqr(rayStart) < closest) {
                closest = hit.get().distanceToSqr(rayStart);
                result = button.index;
            }
        }
        if (reader != null) {
            var hit = reader.clip(start, end);
            if (hit.isPresent() && hit.get().distanceToSqr(start) < closest) result = READER;
        }
        return result;
    }

    public static double pressAmount(long pressedAt, long now) {
        if (pressedAt == 0) return 0;
        double milliseconds = (now - pressedAt) / 1_000_000.0;
        if (milliseconds < 0 || milliseconds >= 240) return 0;
        if (milliseconds < 40) return milliseconds / 40;
        if (milliseconds < 100) return 1;
        double t = (milliseconds - 100) / 140;
        return 1 - t * t * (3 - 2 * t);
    }

    public static int touchIndex(double x,double y) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||y<214||y>668)return -1;
        int side=x>=21&&x<=461?0:x>=563&&x<=1003?1:-1;
        if(side<0)return -1;
        int row=(int)Math.round((y-251)/126);
        return row>=0&&row<4?16+side*4+row:-1;
    }
}
