package me.hesamai.advancedduty.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<g:(#[A-Fa-f0-9]{6}):(#[A-Fa-f0-9]{6})>(.*?)</g>", Pattern.DOTALL);

    private MessageUtil() {
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = applyGradients(text);
        text = applyHex(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final Pattern STRIP_AMP = Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-or])");
    private static final Pattern STRIP_SECTION = Pattern.compile("(?i)\u00A7[0-9a-fk-orx]");
    private static final Pattern STRIP_GRADIENT = Pattern.compile("(?i)</?g(:#[0-9a-f]{6}:#[0-9a-f]{6})?>");

    /** Removes every colour / format / gradient code (both {@code &} and section sign) from a text. */
    public static String stripCodes(String text) {
        if (text == null) return "";
        String out = STRIP_GRADIENT.matcher(text).replaceAll("");
        out = STRIP_AMP.matcher(out).replaceAll("");
        out = STRIP_SECTION.matcher(out).replaceAll("");
        return out;
    }

    /** Colours the text and returns it with all colours removed - handy for logs and Discord. */
    public static String plain(String text) {
        return ChatColor.stripColor(color(text));
    }

    /** Converts coloured text to chat components (legacy parsing, no click/hover injection possible). */
    public static BaseComponent[] components(String coloredText) {
        return TextComponent.fromLegacyText(color(coloredText));
    }

    public static void sendActionBar(Player player, String text) {
        if (player == null) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components(text));
    }

    private static String applyHex(String text) {
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(ChatColor.of("#" + hex).toString()));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String applyGradients(String text) {
        Matcher matcher = GRADIENT_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            Color start = Color.decode(matcher.group(1));
            Color end = Color.decode(matcher.group(2));
            String content = matcher.group(3);

            String replaced = buildGradient(content, start, end);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replaced));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String buildGradient(String content, Color start, Color end) {
        List<TextToken> tokens = tokenize(content);
        int visibleChars = countVisibleCharacters(tokens);

        if (visibleChars == 0) {
            return content;
        }

        StringBuilder out = new StringBuilder();
        String activeFormats = "";
        int visibleIndex = 0;

        for (TextToken token : tokens) {
            if (token.formatCode != null) {
                char lower = Character.toLowerCase(token.formatCode);

                if (lower == 'r') {
                    activeFormats = "";
                } else if (isFormatCode(lower)) {
                    String format = ChatColor.getByChar(lower).toString();
                    if (!activeFormats.contains(format)) {
                        activeFormats += format;
                    }
                } else if (isColorCode(lower)) {
                    activeFormats = "";
                }

                continue;
            }

            float ratio = visibleChars == 1 ? 0.0F : (float) visibleIndex / (float) (visibleChars - 1);
            Color interpolated = interpolate(start, end, ratio);

            out.append(ChatColor.of(interpolated))
                    .append(activeFormats)
                    .append(token.character);

            visibleIndex++;
        }

        return out.toString();
    }

    private static List<TextToken> tokenize(String content) {
        List<TextToken> tokens = new ArrayList<>();

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);

            if (current == '&' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if (isLegacyCode(next)) {
                    tokens.add(TextToken.format(next));
                    i++;
                    continue;
                }
            }

            tokens.add(TextToken.character(current));
        }

        return tokens;
    }

    private static int countVisibleCharacters(List<TextToken> tokens) {
        int count = 0;
        for (TextToken token : tokens) {
            if (token.character != null) {
                count++;
            }
        }
        return count;
    }

    private static boolean isLegacyCode(char c) {
        char lower = Character.toLowerCase(c);
        return isColorCode(lower) || isFormatCode(lower) || lower == 'r';
    }

    private static boolean isColorCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private static boolean isFormatCode(char c) {
        return c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o';
    }

    private static Color interpolate(Color start, Color end, float ratio) {
        int r = (int) (start.getRed() + ratio * (end.getRed() - start.getRed()));
        int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
        int b = (int) (start.getBlue() + ratio * (end.getBlue() - start.getBlue()));
        return new Color(r, g, b);
    }

    private static final class TextToken {
        private final Character character;
        private final Character formatCode;

        private TextToken(Character character, Character formatCode) {
            this.character = character;
            this.formatCode = formatCode;
        }

        private static TextToken character(char c) {
            return new TextToken(c, null);
        }

        private static TextToken format(char code) {
            return new TextToken(null, code);
        }
    }
}