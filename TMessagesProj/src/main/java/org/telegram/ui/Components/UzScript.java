package org.telegram.ui.Components;

/**
 * Uzbek script converter: Cyrillic, current Latin and the new Latin alphabet
 * (sh -> ş, ch -> ç, o' -> ö, g' -> ğ) that comes into force in April 2027.
 * Works only on the phone, no network, pure Java.
 */
public class UzScript {

    public static final int NONE = -1;
    public static final int CYR = 0;
    public static final int LAT = 1; // current Latin
    public static final int NEW = 2; // new Latin (ş ç ö ğ)

    private static final char OKINA = '‘'; // o‘ g‘ like on the Uzbek keyboard
    private static final char TUTUQ = '’';

    /* ---------------- detection ---------------- */

    public static int detect(CharSequence text) {
        int cyr = 0;
        int lat = 0;
        boolean newMark = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) {
                cyr++;
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                lat++;
            } else if ("şçöğŞÇÖĞ".indexOf(c) >= 0) {
                newMark = true;
                lat++;
            }
        }
        if (cyr > 0 && cyr >= lat) {
            return CYR;
        }
        if (newMark) {
            return NEW;
        }
        if (lat > 0) {
            return LAT;
        }
        return NONE;
    }

    public static String convert(String s, int from, int to) {
        if (s == null || s.isEmpty() || from == to || from == NONE) {
            return s;
        }
        String latin;
        switch (from) {
            case CYR: latin = cyrToLat(s); break;
            case NEW: latin = newToLat(s); break;
            default:  latin = s;
        }
        switch (to) {
            case CYR: return latToCyr(latin);
            case NEW: return latToNew(latin);
            default:  return latin;
        }
    }

    /* ---------------- helpers ---------------- */

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == '‘' || c == '’' || c == 'ʻ' || c == 'ʼ' || c == '`' || c == '´';
    }

    private static boolean isLetter(String s, int i) {
        return i >= 0 && i < s.length() && Character.isLetter(s.charAt(i));
    }

    private static boolean isUpper(String s, int i) {
        return i >= 0 && i < s.length() && Character.isUpperCase(s.charAt(i));
    }

    /** Letter in capitals: a whole capitalized word writes digraphs like SH, otherwise Sh. */
    private static String cased(String lower, boolean upper, boolean allCaps) {
        if (!upper) {
            return lower;
        }
        if (allCaps) {
            return lower.toUpperCase();
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /* ---------------- Cyrillic -> Latin ---------------- */

    private static String cyrLetter(char c) {
        switch (c) {
            case 'а': return "a";
            case 'б': return "b";
            case 'в': return "v";
            case 'г': return "g";
            case 'д': return "d";
            case 'ё': return "yo";
            case 'ж': return "j";
            case 'з': return "z";
            case 'и': return "i";
            case 'й': return "y";
            case 'к': return "k";
            case 'л': return "l";
            case 'м': return "m";
            case 'н': return "n";
            case 'о': return "o";
            case 'п': return "p";
            case 'р': return "r";
            case 'с': return "s";
            case 'т': return "t";
            case 'у': return "u";
            case 'ф': return "f";
            case 'х': return "x";
            case 'ц': return "ts";
            case 'ч': return "ch";
            case 'ш': return "sh";
            case 'щ': return "shch";
            case 'ъ': return String.valueOf(TUTUQ);
            case 'ь': return "";
            case 'ы': return "i";
            case 'э': return "e";
            case 'ю': return "yu";
            case 'я': return "ya";
            case 'ў': return "o" + OKINA;
            case 'қ': return "q";
            case 'ғ': return "g" + OKINA;
            case 'ҳ': return "h";
            default:  return null;
        }
    }

    static String cyrToLat(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lc = Character.toLowerCase(c);
            String m;
            if (lc == 'е') {
                // word start or after a vowel: "ye", otherwise "e"
                boolean ye = i == 0 || !Character.isLetter(s.charAt(i - 1))
                        || "аеёиоуўэюяы".indexOf(Character.toLowerCase(s.charAt(i - 1))) >= 0;
                m = ye ? "ye" : "e";
            } else {
                m = cyrLetter(lc);
            }
            if (m == null) {
                sb.append(c);
                continue;
            }
            boolean upper = Character.isUpperCase(c);
            boolean allCaps = upper && (isUpper(s, i - 1) || isUpper(s, i + 1));
            sb.append(cased(m, upper, allCaps));
        }
        return sb.toString();
    }

    /* ---------------- Latin -> Cyrillic ---------------- */

    private static char latSingle(char lc) {
        switch (lc) {
            case 'a': return 'а';
            case 'b': return 'б';
            case 'd': return 'д';
            case 'f': return 'ф';
            case 'g': return 'г';
            case 'h': return 'ҳ';
            case 'i': return 'и';
            case 'j': return 'ж';
            case 'k': return 'к';
            case 'l': return 'л';
            case 'm': return 'м';
            case 'n': return 'н';
            case 'o': return 'о';
            case 'p': return 'п';
            case 'q': return 'қ';
            case 'r': return 'р';
            case 's': return 'с';
            case 't': return 'т';
            case 'u': return 'у';
            case 'v': return 'в';
            case 'x': return 'х';
            case 'y': return 'й';
            case 'z': return 'з';
            case 'c': return 'к'; // c alone does not exist in Uzbek, best guess
            case 'w': return 'в';
            default:  return 0;
        }
    }

    private static void put(StringBuilder sb, char lower, boolean upper) {
        sb.append(upper ? Character.toUpperCase(lower) : lower);
    }

    static String latToCyr(String s) {
        final int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            char lc = Character.toLowerCase(c);
            char nx = i + 1 < n ? s.charAt(i + 1) : 0;
            char lnx = Character.toLowerCase(nx);
            boolean up = Character.isUpperCase(c);

            if (lc == 's' && lnx == 'h') {
                put(sb, 'ш', up);
                i += 2;
            } else if (lc == 'c' && lnx == 'h') {
                put(sb, 'ч', up);
                i += 2;
            } else if (lc == 'y' && (lnx == 'o' || lnx == 'u' || lnx == 'a' || lnx == 'e')) {
                put(sb, lnx == 'o' ? 'ё' : (lnx == 'u' ? 'ю' : (lnx == 'a' ? 'я' : 'е')), up);
                i += 2;
            } else if ((lc == 'o' || lc == 'g') && isApostrophe(nx)) {
                put(sb, lc == 'o' ? 'ў' : 'ғ', up);
                i += 2;
            } else if (lc == 'n' && lnx == 'g' && !(i + 2 < n && isApostrophe(s.charAt(i + 2)))) {
                if (up) {
                    sb.append('Н').append(Character.isUpperCase(nx) ? 'Г' : 'г');
                } else {
                    sb.append("нг");
                }
                i += 2;
            } else if (isApostrophe(c)) {
                if (isLetter(s, i - 1) && isLetter(s, i + 1)) {
                    sb.append('ъ');
                } else {
                    sb.append(c);
                }
                i++;
            } else if (lc == 'e') {
                boolean start = i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1));
                put(sb, start ? 'э' : 'е', up);
                i++;
            } else {
                char m = latSingle(lc);
                if (m == 0) {
                    sb.append(c);
                } else {
                    put(sb, m, up);
                }
                i++;
            }
        }
        return sb.toString();
    }

    /* ---------------- current Latin <-> new Latin ---------------- */

    static String latToNew(String s) {
        final int n = s.length();
        StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            char lc = Character.toLowerCase(c);
            char nx = i + 1 < n ? s.charAt(i + 1) : 0;
            char lnx = Character.toLowerCase(nx);
            boolean up = Character.isUpperCase(c);
            if (lc == 's' && lnx == 'h') {
                sb.append(up ? 'Ş' : 'ş');
                i += 2;
            } else if (lc == 'c' && lnx == 'h') {
                sb.append(up ? 'Ç' : 'ç');
                i += 2;
            } else if (lc == 'o' && isApostrophe(nx)) {
                sb.append(up ? 'Ö' : 'ö');
                i += 2;
            } else if (lc == 'g' && isApostrophe(nx)) {
                sb.append(up ? 'Ğ' : 'ğ');
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    static String newToLat(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean up = Character.isUpperCase(c);
            boolean allCaps = up && (isUpper(s, i - 1) || isUpper(s, i + 1));
            switch (Character.toLowerCase(c)) {
                case 'ş': sb.append(cased("sh", up, allCaps)); break;
                case 'ç': sb.append(cased("ch", up, allCaps)); break;
                case 'ö': sb.append(up ? 'O' : 'o').append(OKINA); break;
                case 'ğ': sb.append(up ? 'G' : 'g').append(OKINA); break;
                default:  sb.append(c);
            }
        }
        return sb.toString();
    }
}
