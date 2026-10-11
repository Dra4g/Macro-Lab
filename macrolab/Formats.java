package macrolab;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Formats {
    private Formats() {}
    static List<List<String>> csv(final String text) throws IOException {
        final List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        final StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = text.startsWith("\uFEFF")
                ? 1
                : 0;
             i < text.length(); i++) {
            final char c;
            if ((c = text
                    .charAt(i)) == '"') {
                if (quoted
                        && i + 1 < text.length()
                        && text
                        .charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                }
                else quoted = !quoted;
            } else if (c == ','
                    && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            }
            else if ((c == '\n'
                    || c == '\r')
                    && !quoted) {
                if (!row
                        .isEmpty()
                        || !cell
                        .isEmpty()) {
                    row.add(cell.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    cell.setLength(0);
                }
            } else cell.append(c);
        }
        if (quoted)
            throw new IOException("CSV: virgolette non chiuse");
        if (!row
                .isEmpty()
                || !cell
                .isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    static Object json(final String text) throws IOException {
        final Json parser = new Json(text);
        final Object result = parser.value(0);
        parser.space();
        if (parser.pos != text.length())
            throw new IOException("JSON: contenuto residuo");
        return result;
    }
    static Map<?, ?> object(final Object value) throws IOException {
        if (value instanceof final Map<?, ?> map)
            return map;
        throw new IOException("JSON: oggetto atteso");
    }
    static List<?> array(final Object value) throws IOException {
        if (value instanceof final List<?> list)
            return list;
        throw new IOException("JSON: elenco atteso");
    }
    static final class Json {
        final String text;
        int pos;
        Json(final String text) { this.text = text; }
        void space() {
            while (pos < text.length()
                    && Character
                    .isWhitespace(text.charAt(pos)))
                pos++;
        }
        char take() throws IOException {
            if (pos >= text.length())
                throw new IOException("JSON incompleto");
            return text.charAt(pos++);
        }
        void expect(final char c) throws IOException {
            space();
            if (take() != c)
                throw new IOException("JSON: atteso " + c); }
        Object value(final int depth) throws IOException {
            if (depth > 64)
                throw new IOException("JSON troppo profondo");
            space();
            if (pos >= text.length())
                throw new IOException("JSON vuoto");
            final char c;
            if ((c = text.charAt(pos)) == '{') {
                pos++;
                final Map<String, Object> map = new LinkedHashMap<>();
                space();
                if (pos < text.length()
                        && text
                        .charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                /* key va letta PRIMA dei due punti; spostarla dentro put cambierebbe
                   l'ordine di consumo del parser. Una lettura sola, in questo punto. */
                do {
                    space();
                    final String key = string();
                    expect(':');
                    map.put(key, value(depth + 1));
                    space();
                    final char end;
                    if ((end = take()) == '}')
                        return map;
                    if (end != ',')
                        throw new IOException("JSON oggetto non valido");
                } while (true);
            }
            if (c == '[') {
                pos++;
                final List<Object> list = new ArrayList<>();
                space();
                if (pos < text.length()
                        && text
                        .charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                do {
                    list.add(value(depth + 1));
                    space();
                    final char end;
                    if ((end = take()) == ']')
                        return list;
                    if (end != ',')
                        throw new IOException("JSON elenco non valido");
                } while (true);
            }
            if (c == '"') return string();
            if (text.startsWith("null", pos)) {
                pos += 4;
                return null; }
            if (text.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE; }
            if (text.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE; }
            /* L'inizio deve restare quello PRIMA della scansione numerica. */
            final int start = pos;
            while (pos < text.length()
                    && "-+0123456789.eE"
                    .indexOf(text.charAt(pos)) >= 0)
                pos++;
            try {
                return Double.valueOf(text.substring(start, pos));
            }
            catch (final NumberFormatException e) {
                throw new IOException("JSON: numero non valido", e);
            }
        }
        String string() throws IOException {
            expect('"');
            final StringBuilder out = new StringBuilder();
            while (true) {
                final char c;
                if ((c = take()) == '"')
                    return out.toString();
                if (c == '\\') {
                    final char escaped;
                    if ((escaped = take()) == 'u') {
                        int code = 0;
                        for (int i = 0; i < 4; i++) {
                            final int digit;
                            if ((digit = Character.digit(take(), 16)) < 0)
                                throw new IOException("JSON: escape unicode non valido");
                            code = (code << 4) | digit;
                        }
                        out.append((char) code);
                    } else out.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        default -> throw new IOException("JSON: escape non valido");
                    });
                } else {
                    if (c < 32)
                        throw new IOException("JSON: carattere di controllo");
                    out.append(c);
                }
            }
        }
    }
}
