package net.austizz.ultimatebankingsystem.wiki;

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** CommonMark with the GFM features used by UBS pages. */
public final class WikiMarkdown {
    private static final Parser PARSER = Parser.builder().extensions(List.of(
            TablesExtension.create(), StrikethroughExtension.create())).build();

    private WikiMarkdown() {}

    public static Node parse(String markdown) {
        return PARSER.parse(markdown);
    }

    public record Media(String kind, String id, int size, boolean tooltip, int width, int height) {}

    /** Local item models, textures, and wiki screenshots are rendered without a web request. */
    public static Optional<Media> media(String destination) {
        try {
            URI uri = URI.create(destination);
            String kind = uri.getScheme();
            if (!"item".equals(kind) && !"texture".equals(kind) && !"screenshot".equals(kind)) return Optional.empty();
            String source = uri.getRawSchemeSpecificPart();
            int question = source.indexOf('?');
            String id = question < 0 ? source : source.substring(0, question);
            if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return Optional.empty();
            int size = 24;
            boolean tooltip = true;
            int width = 0;
            int height = 0;
            if (question >= 0) {
                for (String option : source.substring(question + 1).split("&")) {
                    String[] pair = option.split("=", 2);
                    if (pair.length != 2) continue;
                    if (pair[0].equals("size")) size = Integer.parseInt(pair[1]);
                    if (pair[0].equals("tooltip")) tooltip = Boolean.parseBoolean(pair[1]);
                    if (pair[0].equals("width")) width = Integer.parseInt(pair[1]);
                    if (pair[0].equals("height")) height = Integer.parseInt(pair[1]);
                }
            }
            if ("screenshot".equals(kind)) {
                if (!id.matches("ultimatebankingsystem:[0-9]{2}") || width < 1 || width > 4096 || height < 1 || height > 4096) {
                    return Optional.empty();
                }
            }
            return Optional.of(new Media(kind, id, Math.clamp(size, 16, 96), tooltip, width, height));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
