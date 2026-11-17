package net.austizz.ultimatebankingsystem.npc.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.network.NpcSkinActionPayload;
import net.austizz.ultimatebankingsystem.network.NpcSkinEditorPayload;
import net.austizz.ultimatebankingsystem.network.NpcSkinSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Server-authoritative NPC skin library and synchronized role assignment service. */
public final class NpcSkinService {
    private static final int MAX_SKIN_BYTES = NpcSkinEditorPayload.MAX_SKIN_BYTES;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private NpcSkinService() {
    }

    public static Path skinDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(UltimateBankingSystem.MODID).resolve("npc_skins");
    }

    public static void openEditor(ServerPlayer player) {
        openEditor(player, "");
    }

    public static void openEditor(ServerPlayer player, String notice) {
        if (player == null || !player.hasPermissions(2)) return;
        try {
            Map<String, StoredSkin> skins = scanLibrary();
            NpcSkinSavedData data = NpcSkinSavedData.get(player.server);
            List<NpcSkinEditorPayload.TargetSelection> targets = new ArrayList<>();
            for (NpcSkinTarget target : NpcSkinTarget.values()) {
                String selected = data.assignedSkinId(target);
                if (!selected.isBlank() && !skins.containsKey(selected)) selected = "";
                targets.add(new NpcSkinEditorPayload.TargetSelection(target.id(), target.displayName(), selected,
                        target.defaultTexture().toString(), data.usesSlimModel(target)));
            }
            List<NpcSkinEditorPayload.SkinAsset> assets = skins.values().stream()
                    .map(StoredSkin::toPayload)
                    .toList();
            PacketDistributor.sendToPlayer(player, new NpcSkinEditorPayload(assets, targets, message(notice)));
        } catch (IOException exception) {
            PacketDistributor.sendToPlayer(player, new NpcSkinEditorPayload(List.of(), List.of(),
                    "Could not scan npc_skins: " + rootMessage(exception)));
        }
    }

    public static void handleAction(ServerPlayer player, NpcSkinActionPayload payload) {
        if (player == null || payload == null) return;
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("Only server operators can edit NPC skins.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        String action = clean(payload.action()).toUpperCase(Locale.ROOT);
        switch (action) {
            case "REFRESH" -> openEditor(player, "Skin folder refreshed.");
            case "ASSIGN" -> assign(player, payload.targetId(), payload.value());
            case "SET_MODEL" -> setModel(player, payload.targetId(), payload.value());
            case "UPLOAD" -> upload(player, payload.value(), payload.png());
            case "LOOKUP" -> lookup(player, payload.value());
            default -> openEditor(player, "Unknown NPC skin action.");
        }
    }

    public static void syncPlayer(ServerPlayer player) {
        if (player == null || player.server == null) return;
        try {
            Map<String, StoredSkin> skins = scanLibrary();
            NpcSkinSavedData data = NpcSkinSavedData.get(player.server);
            for (NpcSkinTarget target : NpcSkinTarget.values()) {
                StoredSkin selected = skins.get(data.assignedSkinId(target));
                PacketDistributor.sendToPlayer(player, selected == null
                        ? new NpcSkinSyncPayload(target.id(), "", new byte[0], data.usesSlimModel(target))
                        : new NpcSkinSyncPayload(target.id(), selected.id(), selected.png(), data.usesSlimModel(target)));
            }
        } catch (IOException exception) {
            UltimateBankingSystem.LOGGER.warn("[UBS] Could not synchronize NPC skins to {}: {}",
                    player.getGameProfile().getName(), rootMessage(exception));
        }
    }

    private static void assign(ServerPlayer player, String targetId, String skinId) {
        NpcSkinTarget target = NpcSkinTarget.byId(targetId);
        if (target == null) {
            openEditor(player, "Choose a valid NPC role first.");
            return;
        }
        try {
            Map<String, StoredSkin> skins = scanLibrary();
            String requested = clean(skinId);
            if (!requested.isBlank() && !skins.containsKey(requested)) {
                openEditor(player, "That skin is no longer available. Refresh the library.");
                return;
            }
            NpcSkinSavedData data = NpcSkinSavedData.get(player.server);
            data.assign(target, requested);
            syncTarget(player.server, target, skins.get(requested), data.usesSlimModel(target));
            openEditor(player, requested.isBlank()
                    ? target.displayName() + " restored to its default skin."
                    : target.displayName() + " now uses " + skins.get(requested).displayName() + ".");
        } catch (IOException exception) {
            openEditor(player, "Could not apply skin: " + rootMessage(exception));
        }
    }

    private static void setModel(ServerPlayer player, String targetId, String requestedModel) {
        NpcSkinTarget target = NpcSkinTarget.byId(targetId);
        if (target == null) {
            openEditor(player, "Choose a valid NPC role first.");
            return;
        }
        String model = clean(requestedModel).toUpperCase(Locale.ROOT);
        if (!"CLASSIC".equals(model) && !"SLIM".equals(model)) {
            openEditor(player, "Choose either the Classic or Slim NPC model.");
            return;
        }
        try {
            Map<String, StoredSkin> skins = scanLibrary();
            NpcSkinSavedData data = NpcSkinSavedData.get(player.server);
            boolean slim = "SLIM".equals(model);
            data.setSlimModel(target, slim);
            syncTarget(player.server, target, skins.get(data.assignedSkinId(target)), slim);
            openEditor(player, target.displayName() + " now uses the " + (slim ? "Slim" : "Classic") + " NPC model.");
        } catch (IOException exception) {
            openEditor(player, "Could not update the NPC model: " + rootMessage(exception));
        }
    }

    private static void upload(ServerPlayer player, String selectedName, byte[] png) {
        try {
            validateSkin(png);
            Files.createDirectories(skinDirectory());
            String base = filenameBase(selectedName);
            Path destination = uniquePath(base);
            Files.write(destination, png);
            openEditor(player, "Added " + destination.getFileName() + " to npc_skins.");
        } catch (IllegalArgumentException | IOException exception) {
            openEditor(player, "Skin upload rejected: " + rootMessage(exception));
        }
    }

    private static void lookup(ServerPlayer player, String identity) {
        String normalized = clean(identity);
        if (normalized.isBlank()) {
            openEditor(player, "Enter a Minecraft username or UUID to look up a skin.");
            return;
        }
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        openEditor(player, "Looking up " + normalized + " without blocking the server...");
        CompletableFuture.supplyAsync(() -> downloadSkin(normalized)).whenComplete((download, failure) -> server.execute(() -> {
            ServerPlayer current = server.getPlayerList().getPlayer(playerId);
            if (current == null || !current.hasPermissions(2)) return;
            if (failure != null) {
                openEditor(current, "Skin lookup failed: " + rootMessage(failure));
                return;
            }
            try {
                validateSkin(download.png());
                Files.createDirectories(skinDirectory());
                Path destination = uniquePath("lookup-" + download.name());
                Files.write(destination, download.png());
                openEditor(current, "Added skin for " + download.name() + ". Select it from the library.");
            } catch (IllegalArgumentException | IOException exception) {
                openEditor(current, "Skin lookup rejected: " + rootMessage(exception));
            }
        }));
    }

    private static DownloadedSkin downloadSkin(String identity) {
        try {
            String uuid = identity.replace("-", "");
            if (!uuid.matches("[0-9a-fA-F]{32}")) {
                URI profileUri = URI.create("https://api.mojang.com/users/profiles/minecraft/"
                        + URLEncoder.encode(identity, StandardCharsets.UTF_8));
                JsonObject profile = JsonParser.parseString(new String(getBytes(profileUri, 8 * 1024),
                        StandardCharsets.UTF_8)).getAsJsonObject();
                uuid = profile.get("id").getAsString();
            }
            if (!uuid.matches("[0-9a-fA-F]{32}")) throw new IOException("Mojang did not return a valid UUID.");
            URI sessionUri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
            JsonObject session = JsonParser.parseString(new String(getBytes(sessionUri, 16 * 1024),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray properties = session.getAsJsonArray("properties");
            String encoded = "";
            for (int index = 0; properties != null && index < properties.size(); index++) {
                JsonObject property = properties.get(index).getAsJsonObject();
                if ("textures".equals(property.get("name").getAsString())) {
                    encoded = property.get("value").getAsString();
                    break;
                }
            }
            if (encoded.isBlank()) throw new IOException("No skin texture is available for that profile.");
            JsonObject texture = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            URI skinUri = URI.create(texture.getAsJsonObject("textures").getAsJsonObject("SKIN")
                    .get("url").getAsString());
            if (!"https".equalsIgnoreCase(skinUri.getScheme())
                    || !"textures.minecraft.net".equalsIgnoreCase(skinUri.getHost())) {
                throw new IOException("The profile returned an unsafe skin URL.");
            }
            String name = session.has("name") ? session.get("name").getAsString() : uuid;
            return new DownloadedSkin(filenameBase(name), getBytes(skinUri, MAX_SKIN_BYTES));
        } catch (Exception exception) {
            throw new IllegalStateException(rootMessage(exception));
        }
    }

    private static byte[] getBytes(URI uri, int maximumBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "UltimateBankingSystem-NpcSkinLookup")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " from " + uri.getHost());
        }
        try (InputStream stream = response.body()) {
            byte[] data = stream.readNBytes(maximumBytes + 1);
            if (data.length > maximumBytes) throw new IOException("The skin file exceeds 32 KiB.");
            return data;
        }
    }

    private static void syncTarget(MinecraftServer server, NpcSkinTarget target, StoredSkin skin, boolean slimModel) {
        if (server == null || target == null) return;
        NpcSkinSyncPayload payload = skin == null
                ? new NpcSkinSyncPayload(target.id(), "", new byte[0], slimModel)
                : new NpcSkinSyncPayload(target.id(), skin.id(), skin.png(), slimModel);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(online, payload);
        }
    }

    private static Map<String, StoredSkin> scanLibrary() throws IOException {
        Path directory = skinDirectory();
        Files.createDirectories(directory);
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(NpcSkinEditorPayload.MAX_SKINS)
                    .toList();
        }
        Map<String, StoredSkin> result = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                long size = Files.size(file);
                if (size <= 0L || size > MAX_SKIN_BYTES) {
                    throw new IllegalArgumentException("Skin PNGs must be between 1 byte and 32 KiB.");
                }
                byte[] png = Files.readAllBytes(file);
                validateSkin(png);
                String filename = file.getFileName().toString();
                String id = "file:" + filename.toLowerCase(Locale.ROOT);
                result.put(id, new StoredSkin(id, filenameBase(filename), png));
            } catch (IllegalArgumentException exception) {
                UltimateBankingSystem.LOGGER.warn("[UBS] Ignoring invalid NPC skin {}: {}", file, rootMessage(exception));
            }
        }
        return result;
    }

    private static void validateSkin(byte[] png) {
        if (png == null || png.length == 0 || png.length > MAX_SKIN_BYTES) {
            throw new IllegalArgumentException("PNG skins must be between 1 byte and 32 KiB.");
        }
        try (ByteArrayInputStream stream = new ByteArrayInputStream(png)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                throw new IllegalArgumentException("NPC skins must be a standard 64x64 or legacy 64x32 PNG.");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The file is not a readable PNG skin.", exception);
        }
    }

    private static Path uniquePath(String requestedName) throws IOException {
        Path directory = skinDirectory();
        String stem = filenameBase(requestedName);
        Path destination = directory.resolve(stem + ".png");
        for (int suffix = 2; Files.exists(destination); suffix++) {
            destination = directory.resolve(stem + "-" + suffix + ".png");
        }
        return destination;
    }

    private static String filenameBase(String value) {
        String source = value == null ? "skin" : value.replaceFirst("(?i)\\.png$", "");
        String clean = source.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
        if (clean.isBlank()) clean = "skin";
        return clean.substring(0, Math.min(48, clean.length()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String message(String value) {
        String clean = clean(value);
        return clean.length() <= NpcSkinEditorPayload.MAX_TEXT ? clean
                : clean.substring(0, NpcSkinEditorPayload.MAX_TEXT);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) cursor = cursor.getCause();
        String message = cursor == null ? "Unknown error" : cursor.getMessage();
        return message == null || message.isBlank() ? "Unknown error" : message;
    }

    private record StoredSkin(String id, String displayName, byte[] png) {
        private NpcSkinEditorPayload.SkinAsset toPayload() {
            return new NpcSkinEditorPayload.SkinAsset(id, displayName, png);
        }
    }

    private record DownloadedSkin(String name, byte[] png) {
    }
}
