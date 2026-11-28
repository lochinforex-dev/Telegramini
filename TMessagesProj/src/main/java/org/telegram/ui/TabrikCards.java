package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;

import java.util.Calendar;
import java.util.Locale;

/**
 * Greeting card designs for the "Tabriklar" section. Every design is drawn with Canvas
 * (gradients, simple shapes and line icons), so there are no image files and the same code
 * draws the small previews and the big picture that is sent to a chat.
 */
public final class TabrikCards {

    // background decorations
    private static final int D_NONE = 0, D_CONFETTI = 1, D_FRAME = 2, D_BALLOONS = 3, D_CARD = 4, D_RIBBON = 5,
            D_CLOUDS = 6, D_STRIPES = 7, D_SNOW = 8, D_LIGHTS = 9, D_BOTTOM = 10, D_GROUND = 11,
            D_FIREWORKS = 12, D_STARS = 13, D_GIFTS = 14, D_BUBBLES = 15, D_FLOWERS = 16, D_RAYS = 17,
            D_HEARTS = 18, D_FLAGBAND = 19;
    // icons
    private static final int I_NONE = 0, I_CAKE = 1, I_SPARKLE = 2, I_STAR = 3, I_GIFT = 4, I_CROWN = 5,
            I_BALLOON = 6, I_SNOWFLAKE = 7, I_TREE = 8, I_BELL = 9, I_SNOWMAN = 10, I_CLOCK = 11,
            I_MOON = 12, I_FLOWER = 13, I_SUN = 14, I_HEART = 15, I_BOOK = 16, I_FLAG = 17, I_SHIELD = 18;
    // big line
    private static final int B_NONE = 0, B_YEAR = 1, B_MIDNIGHT = 2;

    public static final class Design {
        final String name;
        final int c1, c2;
        final int mode; // 0 solid, 1 diagonal, 2 vertical
        final int deco, icon, iconColor, text, font, big;
        final int scene; // 0 = simple design, 1..8 = rich animated scene

        Design(String name, int c1, int c2, int mode, int deco, int icon, int iconColor, int text, int font, int big) {
            this(name, c1, c2, mode, deco, icon, iconColor, text, font, big, 0);
        }

        Design(String name, int c1, int c2, int mode, int deco, int icon, int iconColor, int text, int font, int big, int scene) {
            this.scene = scene;
            this.name = name;
            this.c1 = c1;
            this.c2 = c2;
            this.mode = mode;
            this.deco = deco;
            this.icon = icon;
            this.iconColor = iconColor;
            this.text = text;
            this.font = font; // 0 bold, 1 serif italic, 2 bold capitals
            this.big = big;
        }

        public String getName() {
            return name;
        }

        public int getColor() {
            return c1;
        }
    }

    private static final class Cat {
        final String name;
        final String title;
        final String[] tplNames;
        final String[] bodies;
        final Design[] designs;
        final int colorA, colorB;

        Cat(String name, String title, int colorA, int colorB, String[] tplNames, String[] bodies, Design[] designs) {
            this.name = name;
            this.title = title;
            this.colorA = colorA;
            this.colorB = colorB;
            this.tplNames = tplNames;
            this.bodies = bodies;
            this.designs = designs;
        }
    }

    /** A rich animated scene design: only the background colours and the text colour differ. */
    private static Design sc(String name, int c1, int c2, int mode, int text, int scene) {
        return new Design(name, c1, c2, mode, D_NONE, I_NONE, 0xFFFFFFFF, text, 0, B_NONE, scene);
    }

    private static Design dz(String name, int c1, int c2, int mode, int deco, int icon, int iconColor, int text, int font, int big) {
        return new Design(name, c1, c2, mode, deco, icon, iconColor, text, font, big);
    }

    private static final String[] NAMES_FUN = {"Samimiy", "Rasmiy", "Qisqa", "Hazil"};
    private static final String[] NAMES_POEM = {"Samimiy", "Rasmiy", "Qisqa", "She'riy"};

    /** Colours offered in the editor (replace the background of the design). */
    public static final int[][] PALETTE = {
            {0xFFE2557A, 0xFFF6A54B},
            {0xFF8A5BD6, 0xFF4A2E8A},
            {0xFF2B7BD6, 0xFF5AA6E8},
            {0xFF2E8B6E, 0xFF4FB28F},
            {0xFFD9822B, 0xFFF2C14E},
    };

    // ---------------- invitations (the picture is in assets/taklif, see TabrikInvite) ----------------
    private static final String[] NAMES_INVITE = {"Nikoh to'yi", "Sunnat to'yi", "Beshik to'yi", "Yubiley"};
    private static final String[] SAMPLE_INVITE = {"Jasur & Madina", "Muhammad Ali", "", "Anvar Karimov"};
    private static final String[] BODIES_INVITE = {
            "{to}\nSizni va oilangizni nikoh to'yimizga taklif qilamiz\n15 dekabr · 18:00\n“Registon” to'yxonasi\nSamarqand shahri",
            "{to}\nFarzandimiz sunnat to'yiga sizni mehmon bo'lishga chorlaymiz\n20 dekabr · 12:00\nQarshi, “Navro'z” to'yxonasi\nMustaqillik ko'chasi 14",
            "Kichkina malikamiz beshigi to'yiga sizni muhtaram oilangiz bilan taklif qilamiz\n8 dekabr · 15:00\n“Registon” to'yxonasi\nToshkent, Chilonzor, 7-kvartal",
            "60\n{to}\nYubiley bazmiga taklif qilinasiz\n27 dekabr · 17:00\nBuxoro, “Lab-i Hovuz” restorani"
    };

    private static final Cat[] CATS = {
            // ---------------- Tug'ilgan kun ----------------
            new Cat("Tug'ilgan kun", "Tug'ilgan kuningiz muborak!", 0xFFE2557A, 0xFFF6A54B, NAMES_FUN, new String[]{
                    "Aziz {to}! Sizni tug'ilgan kuningiz bilan chin qalbimdan tabriklayman. Sizga sog'lik, baxt, oilaviy farovonlik va barcha orzularingiz ushalishini tilayman. Hayotingiz yorqin va mazmunli bo'lsin!",
                    "Hurmatli {to}! Tug'ilgan kuningiz munosabati bilan sizni samimiy tabriklaymiz. Sizga mustahkam sog'lik, ishlaringizda muvaffaqiyat va oila baxti tilaymiz.",
                    "{to}, tug'ilgan kuningiz muborak! Doim baxtli va sog' bo'ling!",
                    "{to}, yoshingiz yana bir yoshga ulg'aydi, aqlingiz ham shunga yarasha o'ssin! Tug'ilgan kuningiz bilan, tortdan esa menga ham ulush qoldiring!"
            }, new Design[]{
                    sc("Boy: Tort", 0xFF7B6BD6, 0xFFE2557A, 1, 0xFFFFFFFF, 1),
                    sc("Boy: Tort (ko'k)", 0xFF2B7BD6, 0xFF8A5BD6, 1, 0xFFFFFFFF, 1),
                    sc("Boy: Tort (issiq)", 0xFFF6A54B, 0xFFE2557A, 1, 0xFFFFFFFF, 1),
                    dz("Quyosh botishi", 0xFFF6A54B, 0xFFE2557A, 1, D_NONE, I_CAKE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Konfetti", 0xFF7B6BD6, 0xFF3A4FB0, 1, D_CONFETTI, I_SPARKLE, 0xFFF2C14E, 0xFFFFFFFF, 0, B_NONE),
                    dz("Tilla ramka", 0xFF10203A, 0xFF10203A, 0, D_FRAME, I_STAR, 0xFFE8C872, 0xFFE8C872, 1, B_NONE),
                    dz("Sharlar", 0xFFFFD9E4, 0xFFFFD9E4, 0, D_BALLOONS, I_NONE, 0xFFA02A55, 0xFFA02A55, 0, B_NONE),
                    dz("Oq kartochka", 0xFF4FB28F, 0xFF4FB28F, 0, D_CARD, I_GIFT, 0xFF1F5E47, 0xFF1F5E47, 0, B_NONE),
                    dz("Neon", 0xFF0B0B12, 0xFF0B0B12, 0, D_NONE, I_SPARKLE, 0xFF5FF0E0, 0xFFFF5FA8, 2, B_NONE),
                    dz("Lenta", 0xFFC0492F, 0xFFC0492F, 0, D_RIBBON, I_CAKE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Osmon", 0xFF5AA6E8, 0xFFBFE3FF, 2, D_CLOUDS, I_BALLOON, 0xFFE2557A, 0xFF1D4F86, 0, B_NONE),
                    dz("Oltin", 0xFFF2C14E, 0xFFD9822B, 1, D_NONE, I_CROWN, 0xFF4A2A05, 0xFF4A2A05, 0, B_NONE),
                    dz("Kamalak", 0xFFE2557A, 0xFFE2557A, 0, D_STRIPES, I_NONE, 0xFF2A2A2A, 0xFF2A2A2A, 0, B_NONE)
            }),
            // ---------------- Yangi yil ----------------
            new Cat("Yangi yil", "Yangi yil muborak!", 0xFF2B5A9E, 0xFF0F2447, NAMES_FUN, new String[]{
                    "Aziz {to}! Yangi yil bilan chin qalbimdan tabriklayman. Kirib kelayotgan yil sizga sog'lik, xursandchilik va yangi imkoniyatlar olib kelsin. Barcha orzularingiz ushalsin!",
                    "Hurmatli {to}! Sizni Yangi yil bayrami bilan samimiy tabriklaymiz. Yangi yilda ishlaringizga omad, oilangizga farovonlik va tinchlik tilaymiz.",
                    "{to}, Yangi yilingiz muborak! Baxtli va sog' bo'ling!",
                    "{to}, Yangi yilda kamroq ish, ko'proq dam olish va ko'proq mandarin tilayman! Yangi yil bilan!"
            }, new Design[]{
                    sc("Boy: Archa", 0xFF08142E, 0xFF21458A, 2, 0xFFF2D27A, 2),
                    sc("Boy: Archa (binafsha)", 0xFF2A1A4A, 0xFF6B3AA8, 2, 0xFFF2D27A, 2),
                    sc("Boy: Archa (yashil)", 0xFF0F2447, 0xFF13412C, 2, 0xFFF2D27A, 2),
                    dz("Qishki tun", 0xFF0F2447, 0xFF2B5A9E, 2, D_SNOW, I_SNOWFLAKE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Archa", 0xFF13412C, 0xFF13412C, 0, D_LIGHTS, I_TREE, 0xFF4FB28F, 0xFFF2D27A, 0, B_NONE),
                    dz("Qizil va oq", 0xFFC0392F, 0xFFC0392F, 0, D_BOTTOM, I_BELL, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Qorbobo", 0xFFCFE8FB, 0xFFCFE8FB, 0, D_GROUND, I_SNOWMAN, 0xFF1D4F86, 0xFF1D4F86, 0, B_NONE),
                    dz("Salyut", 0xFF0A0A14, 0xFF0A0A14, 0, D_FIREWORKS, I_NONE, 0xFFF2C14E, 0xFFFFFFFF, 0, B_YEAR),
                    dz("Yarim tun", 0xFF2A1A4A, 0xFF2A1A4A, 0, D_NONE, I_CLOCK, 0xFFF2C14E, 0xFFFFFFFF, 0, B_MIDNIGHT),
                    dz("Tilla yulduzlar", 0xFF10203A, 0xFF10203A, 0, D_STARS, I_NONE, 0xFFE8C872, 0xFFE8C872, 1, B_NONE),
                    dz("Konfetti", 0xFFE2557A, 0xFF6B3AA8, 1, D_CONFETTI, I_NONE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_YEAR),
                    dz("Sovg'alar", 0xFF2E8B6E, 0xFF2E8B6E, 0, D_GIFTS, I_NONE, 0xFF1F5E47, 0xFF1F5E47, 0, B_NONE),
                    dz("Shampan", 0xFFF2D27A, 0xFFD9A02B, 1, D_BUBBLES, I_SPARKLE, 0xFF4A2A05, 0xFF4A2A05, 0, B_NONE)
            }),
            // ---------------- Hayit ----------------
            new Cat("Hayit", "Hayitingiz muborak!", 0xFF1B7F6A, 0xFF0E4B3A, NAMES_POEM, new String[]{
                    "Aziz {to}! Sizni muborak Hayit bayrami bilan chin qalbimdan tabriklayman. Qilgan ibodat va duolaringiz qabul bo'lsin. Oilangizga sog'lik, baxt va tinchlik tilayman!",
                    "Hurmatli {to}! Sizni Hayit bayrami bilan samimiy tabriklaymiz. Bu ulug' kunlar uyingizga baraka, oilangizga tinchlik va farovonlik olib kelsin.",
                    "{to}, Hayitingiz muborak bo'lsin! Duolaringiz ijobat bo'lsin!",
                    "{to}, Hayitingiz muborak bo'lsin,\nUyingizga baraka to'lsin.\nDuolaringiz ijobat bo'lib,\nDillaringiz nurga to'lsin!"
            }, new Design[]{
                    sc("Boy: Hilol", 0xFF07263A, 0xFF15705A, 2, 0xFFF2D27A, 3),
                    sc("Boy: Hilol (ko'k)", 0xFF0C1B3A, 0xFF1F4E8C, 2, 0xFFF2D27A, 3),
                    sc("Boy: Hilol (tun)", 0xFF1A1033, 0xFF3A2A6B, 2, 0xFFF2D27A, 3),
                    dz("Tungi osmon", 0xFF0C1B3A, 0xFF1F4E8C, 2, D_STARS, I_MOON, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE),
                    dz("Zumrad", 0xFF0E4B3A, 0xFF0E4B3A, 0, D_FRAME, I_MOON, 0xFFF2D27A, 0xFFF2D27A, 1, B_NONE),
                    dz("Oq kartochka", 0xFF1F8A6A, 0xFF1F8A6A, 0, D_CARD, I_MOON, 0xFF1F8A6A, 0xFF14513E, 0, B_NONE),
                    dz("Tilla", 0xFFF2D27A, 0xFFD9A02B, 1, D_NONE, I_STAR, 0xFF4A2A05, 0xFF4A2A05, 0, B_NONE),
                    dz("Chiroqlar", 0xFF14284B, 0xFF14284B, 0, D_LIGHTS, I_MOON, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE),
                    dz("Yashil osmon", 0xFF1B7F6A, 0xFF0E4B3A, 2, D_STARS, I_SPARKLE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Qum rangi", 0xFFF3E7CF, 0xFFF3E7CF, 0, D_FRAME, I_MOON, 0xFF8A5A1F, 0xFF6B4423, 1, B_NONE),
                    dz("Binafsha", 0xFF3A2A6B, 0xFF6B4AA8, 1, D_BUBBLES, I_MOON, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE),
                    dz("Moviy", 0xFF2B7BD6, 0xFF5AA6E8, 2, D_CLOUDS, I_STAR, 0xFFFFFFFF, 0xFF0E2F5A, 0, B_NONE),
                    dz("Qora va oltin", 0xFF0B0B12, 0xFF0B0B12, 0, D_NONE, I_MOON, 0xFFE8C872, 0xFFE8C872, 2, B_NONE)
            }),
            // ---------------- Navro'z ----------------
            new Cat("Navro'z", "Navro'z bayrami muborak!", 0xFFFFB347, 0xFF2FAE7A, NAMES_FUN, new String[]{
                    "Aziz {to}! Sizni Navro'z bayrami bilan chin qalbimdan tabriklayman. Bahor kabi hayotingiz ham yashnab, uyingizga baraka, dilingizga xursandchilik kirib kelsin!",
                    "Hurmatli {to}! Sizni Navro'z bayrami bilan samimiy tabriklaymiz. Yangi bahor sizga sog'lik, oilangizga baxt va ishlaringizga omad olib kelsin.",
                    "{to}, Navro'z muborak! Bahoringiz gulga to'lsin!",
                    "{to}, Navro'z muborak! Sumalak shirin, dasturxon to'kin, kayfiyat a'lo bo'lsin!"
            }, new Design[]{
                    sc("Boy: Bahor", 0xFF7EC8F0, 0xFFCDEFFF, 2, 0xFF1D4F86, 4),
                    sc("Boy: Bahor (quyosh)", 0xFFFFE066, 0xFFF6A54B, 1, 0xFF5A2E05, 4),
                    sc("Boy: Bahor (yashil)", 0xFFB7E4C7, 0xFF74C69D, 2, 0xFF1F5E47, 4),
                    dz("Bahor", 0xFF7BD389, 0xFF2E8B6E, 2, D_FLOWERS, I_SUN, 0xFFFFE066, 0xFFFFFFFF, 0, B_NONE),
                    dz("Quyosh", 0xFFFFE066, 0xFFF6A54B, 1, D_RAYS, I_SUN, 0xFFD9822B, 0xFF5A2E05, 0, B_NONE),
                    dz("Oq kartochka", 0xFF4FB28F, 0xFF4FB28F, 0, D_CARD, I_FLOWER, 0xFFE2557A, 0xFF1F5E47, 0, B_NONE),
                    dz("Lola", 0xFFFFD9E4, 0xFFFFD9E4, 0, D_FLOWERS, I_FLOWER, 0xFFE2557A, 0xFFA02A55, 0, B_NONE),
                    dz("Osmon", 0xFF5AA6E8, 0xFFBFE3FF, 2, D_CLOUDS, I_SUN, 0xFFF6A54B, 0xFF1D4F86, 0, B_NONE),
                    dz("Yashil ramka", 0xFF14513E, 0xFF14513E, 0, D_FRAME, I_FLOWER, 0xFFF2D27A, 0xFFF2D27A, 1, B_NONE),
                    dz("Konfetti", 0xFF1FA67A, 0xFF0E6B52, 1, D_CONFETTI, I_SUN, 0xFFFFE066, 0xFFFFFFFF, 0, B_NONE),
                    dz("Qog'oz", 0xFFF7EFE0, 0xFFF7EFE0, 0, D_NONE, I_FLOWER, 0xFF2E8B6E, 0xFF4A3A1A, 1, B_NONE),
                    dz("Binafsha bahor", 0xFFE6DCF7, 0xFFE6DCF7, 0, D_FLOWERS, I_FLOWER, 0xFF8A5BD6, 0xFF4A2E8A, 0, B_NONE),
                    dz("Tilla bahor", 0xFFF2D27A, 0xFFD9A02B, 1, D_RAYS, I_FLOWER, 0xFFC0492F, 0xFF4A2A05, 0, B_NONE)
            }),
            // ---------------- 8-mart ----------------
            new Cat("8-mart", "8-mart bayrami muborak!", 0xFFE2557A, 0xFF8A5BD6, NAMES_FUN, new String[]{
                    "Aziz {to}! Sizni Xalqaro xotin-qizlar kuni bilan chin qalbimdan tabriklayman. Doimo go'zal, baxtli va sevimli bo'ling. Uyingizda tinchlik, dilingizda iliqlik hukm sursin!",
                    "Hurmatli {to}! Sizni 8-mart bayrami bilan samimiy tabriklaymiz. Sizga mustahkam sog'lik, oilaviy baxt va barcha ishlaringizda muvaffaqiyat tilaymiz.",
                    "{to}, 8-mart bayramingiz muborak! Doim tabassum qiling!",
                    "{to}, 8-mart muborak! Bugun siz dam oling, qolgan ishlarni biz hal qilamiz (ertagacha)!"
            }, new Design[]{
                    sc("Boy: Gullar", 0xFFF9B2C8, 0xFFD9487A, 1, 0xFFFFFFFF, 5),
                    sc("Boy: Gullar (binafsha)", 0xFF8A5BD6, 0xFF4A2E8A, 1, 0xFFFFFFFF, 5),
                    sc("Boy: Gullar (issiq)", 0xFFF6A54B, 0xFFE2557A, 1, 0xFFFFFFFF, 5),
                    dz("Pushti", 0xFFF6A5C0, 0xFFE2557A, 1, D_HEARTS, I_FLOWER, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Lola", 0xFFFFD9E4, 0xFFFFD9E4, 0, D_FLOWERS, I_FLOWER, 0xFFE2557A, 0xFFA02A55, 0, B_NONE),
                    dz("Oq kartochka", 0xFF8A5BD6, 0xFF8A5BD6, 0, D_CARD, I_HEART, 0xFFE2557A, 0xFF4A2E8A, 0, B_NONE),
                    dz("Binafsha", 0xFF8A5BD6, 0xFF4A2E8A, 1, D_BUBBLES, I_FLOWER, 0xFFFFD9E4, 0xFFFFFFFF, 0, B_NONE),
                    dz("Tilla ramka", 0xFF3A1F4A, 0xFF3A1F4A, 0, D_FRAME, I_FLOWER, 0xFFE8C872, 0xFFE8C872, 1, B_NONE),
                    dz("Yurak", 0xFFE2557A, 0xFFE2557A, 0, D_HEARTS, I_HEART, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Konfetti", 0xFFF6A54B, 0xFFE2557A, 1, D_CONFETTI, I_SPARKLE, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Sharlar", 0xFFFFF0F5, 0xFFFFF0F5, 0, D_BALLOONS, I_NONE, 0xFFA02A55, 0xFFA02A55, 0, B_NONE),
                    dz("Neon", 0xFF0B0B12, 0xFF0B0B12, 0, D_NONE, I_HEART, 0xFFFF5FA8, 0xFFFF5FA8, 2, B_NONE),
                    dz("Yumshoq", 0xFFE6DCF7, 0xFFFFD9E4, 1, D_FLOWERS, I_FLOWER, 0xFF8A5BD6, 0xFF4A2E8A, 1, B_NONE)
            }),
            // ---------------- Mustaqillik kuni ----------------
            new Cat("Mustaqillik kuni", "Mustaqillik kuni muborak!", 0xFF1E9BD7, 0xFF1FA24A, NAMES_POEM, new String[]{
                    "Aziz {to}! Sizni Mustaqillik kuni bilan chin qalbimdan tabriklayman. Yurtimiz tinch, osmonimiz musaffo, xalqimiz baxtli bo'lsin. Vatanimiz yanada gullab-yashnasin!",
                    "Hurmatli {to}! Sizni O'zbekiston Respublikasining Mustaqillik kuni bilan samimiy tabriklaymiz. Yurtimizga tinchlik, xalqimizga farovonlik va yangi zafarlar tilaymiz.",
                    "{to}, Mustaqillik kuningiz muborak! Vatanimiz abadiy bo'lsin!",
                    "{to}, Vatanim, oltin beshigim,\nSenga baxt tilayman har onda.\nMustaqillik bayrami muborak,\nTinch bo'lsin yurt, olam jahonda!"
            }, new Design[]{
                    sc("Boy: Bayroq", 0xFF0A5F9E, 0xFF0A3A6B, 2, 0xFFFFFFFF, 6),
                    sc("Boy: Bayroq (yashil)", 0xFF14732F, 0xFF1FA24A, 1, 0xFFFFFFFF, 6),
                    sc("Boy: Bayroq (qora)", 0xFF0B0B12, 0xFF1C2233, 2, 0xFFFFFFFF, 6),
                    dz("Bayroq", 0xFF1E9BD7, 0xFF0A5F9E, 2, D_FLAGBAND, I_FLAG, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Oq kartochka", 0xFF1FA24A, 0xFF1FA24A, 0, D_CARD, I_FLAG, 0xFF1FA24A, 0xFF0F5A24, 0, B_NONE),
                    dz("Tilla yulduzlar", 0xFF0A3A6B, 0xFF0A3A6B, 0, D_STARS, I_STAR, 0xFFF2D27A, 0xFFF2D27A, 1, B_NONE),
                    dz("Salyut", 0xFF0A0A14, 0xFF0A0A14, 0, D_FIREWORKS, I_FLAG, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Oq", 0xFFF5F8FB, 0xFFF5F8FB, 0, D_FLAGBAND, I_STAR, 0xFF1E9BD7, 0xFF0A5F9E, 0, B_NONE),
                    dz("Yashil", 0xFF14732F, 0xFF1FA24A, 1, D_CONFETTI, I_FLAG, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Ramka", 0xFF0A5F9E, 0xFF0A5F9E, 0, D_FRAME, I_STAR, 0xFFFFFFFF, 0xFFFFFFFF, 1, B_NONE),
                    dz("Osmon", 0xFF5AA6E8, 0xFFBFE3FF, 2, D_CLOUDS, I_FLAG, 0xFFFFFFFF, 0xFF0A3A6B, 0, B_NONE),
                    dz("Oltin", 0xFFF2C14E, 0xFFD9822B, 1, D_NONE, I_STAR, 0xFF0A3A6B, 0xFF0A3A6B, 0, B_NONE),
                    dz("Qora", 0xFF0B0B12, 0xFF0B0B12, 0, D_STARS, I_FLAG, 0xFFFFFFFF, 0xFFFFFFFF, 2, B_NONE)
            }),
            // ---------------- O'qituvchi va murabbiylar kuni ----------------
            new Cat("O'qituvchilar kuni", "O'qituvchilar kuni muborak!", 0xFFF6A54B, 0xFFC0492F, NAMES_POEM, new String[]{
                    "Aziz {to}! Sizni O'qituvchi va murabbiylar kuni bilan chin qalbimdan tabriklayman. Bergan bilim va tarbiyangiz uchun chuqur minnatdorchilik bildiraman. Sizga sog'lik, sabr va ishlaringizda omad tilayman!",
                    "Hurmatli {to}! Sizni O'qituvchi va murabbiylar kuni bilan samimiy tabriklaymiz. Mehnatingiz va fidoyiligingiz uchun katta rahmat. Sizga mustahkam sog'lik va yangi yutuqlar tilaymiz.",
                    "{to}, O'qituvchi va murabbiylar kuni muborak! Sizga katta rahmat!",
                    "{to}, Siz berdingiz bizga ilm nurini,\nKo'rsatdingiz haqiqat yo'lini.\nBayramingiz muborak bo'lsin,\nBaxt ila to'lsin har bir kuningiz!"
            }, new Design[]{
                    sc("Boy: Kitoblar", 0xFF2B7BD6, 0xFF1D4F86, 1, 0xFFFFFFFF, 7),
                    sc("Boy: Kitoblar (yashil)", 0xFF1F4E3A, 0xFF2E8B6E, 1, 0xFFFFFFFF, 7),
                    sc("Boy: Kitoblar (binafsha)", 0xFF8A5BD6, 0xFF4A2E8A, 1, 0xFFFFFFFF, 7),
                    dz("Kitob", 0xFF2B7BD6, 0xFF1D4F86, 1, D_NONE, I_BOOK, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Oq kartochka", 0xFFF6A54B, 0xFFF6A54B, 0, D_CARD, I_BOOK, 0xFFC0492F, 0xFF5A2E05, 0, B_NONE),
                    dz("Doska", 0xFF1F4E3A, 0xFF1F4E3A, 0, D_FRAME, I_BOOK, 0xFFFFFFFF, 0xFFFFFFFF, 1, B_NONE),
                    dz("Gul", 0xFFFFD9E4, 0xFFFFD9E4, 0, D_FLOWERS, I_BOOK, 0xFFE2557A, 0xFFA02A55, 0, B_NONE),
                    dz("Oltin", 0xFFF2C14E, 0xFFD9822B, 1, D_NONE, I_STAR, 0xFF4A2A05, 0xFF4A2A05, 0, B_NONE),
                    dz("Konfetti", 0xFF8A5BD6, 0xFF4A2E8A, 1, D_CONFETTI, I_BOOK, 0xFFF2C14E, 0xFFFFFFFF, 0, B_NONE),
                    dz("Osmon", 0xFF5AA6E8, 0xFFBFE3FF, 2, D_CLOUDS, I_BOOK, 0xFF1D4F86, 0xFF1D4F86, 0, B_NONE),
                    dz("Qog'oz", 0xFFF7EFE0, 0xFFF7EFE0, 0, D_FRAME, I_BOOK, 0xFF6B4423, 0xFF6B4423, 1, B_NONE),
                    dz("Yashil", 0xFF2E8B6E, 0xFF2E8B6E, 0, D_CARD, I_STAR, 0xFF2E8B6E, 0xFF1F5E47, 0, B_NONE),
                    dz("Tun", 0xFF10203A, 0xFF10203A, 0, D_STARS, I_BOOK, 0xFFE8C872, 0xFFE8C872, 0, B_NONE)
            }),
            // ---------------- Vatan himoyachilari kuni ----------------
            new Cat("Vatan himoyachilari", "Vatan himoyachilari kuni muborak!", 0xFF3A4668, 0xFF14284B, NAMES_POEM, new String[]{
                    "Aziz {to}! Sizni Vatan himoyachilari kuni bilan chin qalbimdan tabriklayman. Yurtimiz tinchligi va osoyishtaligi yo'lida ko'rsatgan jasoratingiz uchun rahmat. Sizga sog'lik va baxt tilayman!",
                    "Hurmatli {to}! Sizni Vatan himoyachilari kuni bilan samimiy tabriklaymiz. Vatan xizmatidagi fidoyiligingiz uchun minnatdorchilik bildiramiz. Sizga mustahkam sog'lik va oilaviy baxt tilaymiz.",
                    "{to}, Vatan himoyachilari kuningiz muborak! Sog' bo'ling!",
                    "{to}, Vatan posbonlari yurtning tayanchi,\nSizlar bilan tinch bizning osmonimiz.\nBayramingiz muborak, baxtli bo'ling,\nDoim sog' bo'lsin jasur jonimiz!"
            }, new Design[]{
                    sc("Boy: Qalqon", 0xFF14284B, 0xFF0A1630, 2, 0xFFFFFFFF, 8),
                    sc("Boy: Qalqon (yashil)", 0xFF14513E, 0xFF1F8A6A, 1, 0xFFFFFFFF, 8),
                    sc("Boy: Qalqon (kulrang)", 0xFF3A4668, 0xFF232C47, 1, 0xFFFFFFFF, 8),
                    dz("Qalqon", 0xFF14284B, 0xFF14284B, 0, D_STARS, I_SHIELD, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE),
                    dz("Yashil", 0xFF14513E, 0xFF1F8A6A, 2, D_FRAME, I_SHIELD, 0xFFF2D27A, 0xFFF2D27A, 1, B_NONE),
                    dz("Oq kartochka", 0xFF0A5F9E, 0xFF0A5F9E, 0, D_CARD, I_SHIELD, 0xFF0A5F9E, 0xFF0A3A6B, 0, B_NONE),
                    dz("Tilla", 0xFFF2C14E, 0xFFD9822B, 1, D_NONE, I_SHIELD, 0xFF4A2A05, 0xFF4A2A05, 0, B_NONE),
                    dz("Salyut", 0xFF0A0A14, 0xFF0A0A14, 0, D_FIREWORKS, I_STAR, 0xFFF2C14E, 0xFFFFFFFF, 0, B_NONE),
                    dz("Bayroq", 0xFF1E9BD7, 0xFF0A5F9E, 2, D_FLAGBAND, I_STAR, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Qora", 0xFF0B0B12, 0xFF0B0B12, 0, D_FRAME, I_SHIELD, 0xFFE8C872, 0xFFE8C872, 2, B_NONE),
                    dz("Qishki", 0xFF0F2447, 0xFF2B5A9E, 2, D_SNOW, I_SHIELD, 0xFFFFFFFF, 0xFFFFFFFF, 0, B_NONE),
                    dz("Qizil", 0xFF8A1F1F, 0xFFC0392F, 1, D_STARS, I_STAR, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE),
                    dz("Kulrang", 0xFF3A4668, 0xFF232C47, 1, D_NONE, I_SHIELD, 0xFFF2D27A, 0xFFFFFFFF, 0, B_NONE)
            }),
            // ---------------- Taklifnoma ----------------
            new Cat("Taklifnoma", "", 0xFF1D3A8A, 0xFFD9A441, NAMES_INVITE, BODIES_INVITE, inviteDesigns())
    };

    private TabrikCards() {
    }

    public static int categoryCount() {
        return CATS.length;
    }

    public static String categoryName(int cat) {
        return CATS[safe(cat)].name;
    }

    public static int[] categoryColors(int cat) {
        return new int[]{CATS[safe(cat)].colorA, CATS[safe(cat)].colorB};
    }

    public static String[] templateNames(int cat) {
        return CATS[safe(cat)].tplNames;
    }

    public static Design[] designs(int cat) {
        return CATS[safe(cat)].designs;
    }

    private static Design[] inviteDesigns() {
        final int n = TabrikInvite.VARIANTS;
        final Design[] list = new Design[NAMES_INVITE.length * n];
        for (int t = 0; t < NAMES_INVITE.length; t++) {
            for (int v = 0; v < n; v++) {
                list[t * n + v] = sc(NAMES_INVITE[t] + " " + (v + 1), 0xFF14285C, 0xFF14285C, 0, 0xFFFFFFFF,
                        TabrikInvite.FIRST_SCENE + t * n + v);
            }
        }
        return list;
    }

    /** Index of the invitation category (it has its own page in Kundalik). */
    public static int inviteCategory() {
        for (int i = 0; i < CATS.length; i++) {
            if (CATS[i].designs[0].scene >= TabrikInvite.FIRST_SCENE) {
                return i;
            }
        }
        return 0;
    }

    /** True for the invitation category (its cards come from TabrikInvite). */
    public static boolean isInvitation(int cat) {
        return CATS[safe(cat)].designs[0].scene >= TabrikInvite.FIRST_SCENE;
    }

    public static String title(int cat) {
        return CATS[safe(cat)].title;
    }

    private static int safe(int cat) {
        return Math.max(0, Math.min(CATS.length - 1, cat));
    }

    /** Ready-made text with the name put in. */
    public static String template(int cat, int index, String name) {
        String[] bodies = CATS[safe(cat)].bodies;
        String raw = bodies[Math.max(0, Math.min(bodies.length - 1, index))];
        String n = name == null ? "" : name.trim();
        if (isInvitation(cat)) {
            final int si = Math.max(0, Math.min(SAMPLE_INVITE.length - 1, index));
            return raw.replace("{to}", n.isEmpty() ? SAMPLE_INVITE[si] : n);
        }
        if (raw.startsWith("{to}")) {
            return (n.isEmpty() ? "Do'stim" : n) + raw.substring(4);
        }
        return raw.replace("{to}", n.isEmpty() ? "do'stim" : n);
    }

    private static int newYear() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) + (c.get(Calendar.MONTH) >= 6 ? 1 : 0);
    }

    /* ======================================================================== */

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final Path PATH2 = new Path();
    private static final RectF RECT = new RectF();

    /**
     * Draws a card. The canvas must be about w x (w * 4 / 3) pixels.
     *
     * @param tint index in {@link #PALETTE} or -1 for the original colours
     */
    public static void draw(Canvas c, float w, float h, Design d, int tint, String title, String body, String sig) {
        draw(c, w, h, d, tint, title, body, sig, -1, null);
    }

    /** Something drawn between the background and the text (falling confetti, snow, stars). */
    public interface Overlay {
        void draw(Canvas canvas, float w, float h);
    }

    private static CharSequence mask(String text, int visible) {
        if (text == null) {
            return "";
        }
        if (visible >= text.length()) {
            return text;
        }
        SpannableString sp = new SpannableString(text);
        sp.setSpan(new ForegroundColorSpan(0x00000000), Math.max(0, visible), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /**
     * @param reveal how many letters of title + text + signature are shown (-1 = all). The layout
     *               always uses the full text, so nothing jumps while the text appears.
     * @param behind drawn after the background and before the text, may be null
     */
    public static void draw(Canvas c, float w, float h, Design d, int tint, String title, String body, String sig,
                            int reveal, Overlay behind) {
        draw(c, w, h, d, tint, title, body, sig, reveal, behind, 0.3f);
    }

    /** @param time position in the animation loop, 0..1 (only rich scenes move) */
    public static void draw(Canvas c, float w, float h, Design d, int tint, String title, String body, String sig,
                            int reveal, Overlay behind, float time) {
        draw(c, w, h, d, tint, title, body, sig, reveal, behind, time, 0f);
    }

    /**
     * @param forcedBodySize text size to use (card units); 0 = find the best size. An animation can ask once
     *                       and then pass the answer back, which saves a lot of time on every frame.
     * @return the body text size that was used
     */
    public static float draw(Canvas c, float w, float h, Design d, int tint, String title, String body, String sig,
                             int reveal, Overlay behind, float time, float forcedBodySize) {
        if (d.scene >= TabrikInvite.FIRST_SCENE) {
            return TabrikInvite.draw(c, w, h, d.scene, body, reveal, behind);
        }
        final float s = w / 300f;
        FILL.setStyle(Paint.Style.FILL);
        FILL.setShader(null);

        int c1 = d.c1;
        int c2 = d.c2;
        int textColor = d.text;
        final boolean card = d.deco == D_CARD || d.deco == D_STRIPES || d.deco == D_GIFTS;
        if (tint >= 0 && tint < PALETTE.length && d.deco != D_STRIPES) {
            c1 = PALETTE[tint][0];
            c2 = PALETTE[tint][1];
            if (!card) {
                textColor = ColorUtils.calculateLuminance(c1) > 0.55 ? 0xFF222222 : 0xFFFFFFFF;
            }
        }

        // ---- background
        if (d.mode == 0 || c1 == c2) {
            FILL.setColor(c1);
        } else if (d.mode == 1) {
            FILL.setShader(new LinearGradient(0, 0, w, h, c1, c2, Shader.TileMode.CLAMP));
        } else {
            FILL.setShader(new LinearGradient(0, 0, 0, h, c1, c2, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, FILL);
        FILL.setShader(null);

        if (d.deco == D_STRIPES) {
            final int[] cols = {0xFFE2557A, 0xFFF6A54B, 0xFFF2C14E, 0xFF4FB28F, 0xFF2B7BD6};
            for (int i = 0; i < 5; i++) {
                FILL.setColor(cols[i]);
                c.drawRect(0, h * i / 5f, w, h * (i + 1) / 5f, FILL);
            }
        }

        drawDecoration(c, w, h, s, d, textColor);
        if (behind != null) {
            behind.draw(c, w, h);
        }
        if (d.scene != 0) {
            drawScene(c, s, d.scene, time);
        }

        // ---- text blocks
        final boolean upper = d.font == 2;
        final String t = upper ? title.toUpperCase(Locale.getDefault()) : title;
        final float cardPad = card ? 16f * s : 0f;
        final float textWidth = (card ? 216f : 240f) * s;

        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(d.deco == D_RIBBON ? 0xFFC0492F : textColor);
        if (d.font == 1) {
            titlePaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            titlePaint.setTextSize(23f * s);
        } else {
            titlePaint.setTypeface(AndroidUtilities.bold());
            titlePaint.setTextSize(21f * s);
        }
        final String bodyStr = body == null ? "" : body;
        final String sigStr = sig == null ? "" : sig;
        final int visTitle = reveal < 0 ? Integer.MAX_VALUE : Math.min(reveal, t.length());
        final int visBody = reveal < 0 ? Integer.MAX_VALUE : Math.max(0, Math.min(reveal - t.length(), bodyStr.length()));
        final int visSig = reveal < 0 ? Integer.MAX_VALUE
                : Math.max(0, Math.min(reveal - t.length() - bodyStr.length(), sigStr.length()));
        StaticLayout titleLayout = layout(mask(t, visTitle), titlePaint, (int) textWidth);

        final float regionTop = d.scene != 0 ? 222f * s : (d.deco == D_BALLOONS ? 100f : 30f) * s + cardPad;
        float bottomReserve = d.scene != 0 ? 22f : 30f;
        if (d.deco == D_BOTTOM) {
            bottomReserve = 66f;
        } else if (d.deco == D_FLAGBAND) {
            bottomReserve = 50f;
        } else if (d.deco == D_GROUND) {
            bottomReserve = 60f;
        }
        final float regionBottom = h - bottomReserve * s - cardPad;
        final float regionH = Math.max(40f * s, regionBottom - regionTop);

        final float iconSize = d.icon != I_NONE ? 54f * s : 0f;
        final float iconGap = d.icon != I_NONE ? 10f * s : 0f;
        final float bigSize = d.big != B_NONE ? 38f * s : 0f;
        final float bigGap = d.big != B_NONE ? 8f * s : 0f;
        final float gap = 8f * s;

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(textColor);
        bodyPaint.setTypeface(d.font == 1 ? Typeface.create(Typeface.SERIF, Typeface.ITALIC) : Typeface.DEFAULT);
        TextPaint sigPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        sigPaint.setColor(textColor);
        sigPaint.setTypeface(AndroidUtilities.bold());
        sigPaint.setTextSize(12f * s);

        final boolean hasBody = body != null && !body.trim().isEmpty();
        final boolean hasSig = sig != null && !sig.trim().isEmpty();
        StaticLayout sigLayout = hasSig ? layout(mask(sigStr, visSig), sigPaint, (int) textWidth) : null;
        final float sigH = hasSig ? gap + sigLayout.getHeight() : 0f;

        StaticLayout bodyLayout = null;
        float total = 0;
        float usedSize = 14f;
        final float startSize = forcedBodySize > 0 ? forcedBodySize : 14f;
        final float minSize = forcedBodySize > 0 ? forcedBodySize : 7.5f;
        for (float size = startSize; size >= minSize - 0.001f; size -= 0.5f) {
            usedSize = size;
            bodyPaint.setTextSize(size * s);
            bodyLayout = hasBody ? layout(mask(bodyStr, visBody), bodyPaint, (int) textWidth) : null;
            total = iconSize + iconGap + bigSize + bigGap + titleLayout.getHeight()
                    + (hasBody ? gap + bodyLayout.getHeight() : 0f) + sigH;
            if (total <= regionH) {
                break;
            }
        }

        float y = regionTop + Math.max(0f, (regionH - total) / 2f);
        final float cx = w / 2f;

        if (card) {
            FILL.setColor(0xFFFFFFFF);
            RECT.set(cx - textWidth / 2f - cardPad, y - cardPad, cx + textWidth / 2f + cardPad, y + total + cardPad);
            c.drawRoundRect(RECT, 16f * s, 16f * s, FILL);
        }

        if (d.icon != I_NONE) {
            drawIcon(c, d.icon, cx, y + iconSize / 2f, iconSize, d.iconColor, s);
            y += iconSize + iconGap;
        }
        if (d.big != B_NONE) {
            TextPaint bp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            bp.setTypeface(AndroidUtilities.bold());
            bp.setTextSize(36f * s);
            bp.setColor(d.iconColor);
            bp.setTextAlign(Paint.Align.CENTER);
            String bigText = d.big == B_YEAR ? String.valueOf(newYear()) : "00:00";
            c.drawText(bigText, cx, y + 30f * s, bp);
            y += bigSize + bigGap;
        }

        if (d.deco == D_RIBBON) {
            c.save();
            c.rotate(-6f, cx, y + titleLayout.getHeight() / 2f);
            FILL.setColor(0xFFFFFFFF);
            RECT.set(-40f * s, y - 8f * s, w + 40f * s, y + titleLayout.getHeight() + 8f * s);
            c.drawRect(RECT, FILL);
            drawLayout(c, titleLayout, cx - textWidth / 2f, y);
            c.restore();
        } else {
            drawLayout(c, titleLayout, cx - textWidth / 2f, y);
        }
        y += titleLayout.getHeight();

        if (hasBody) {
            y += gap;
            drawLayout(c, bodyLayout, cx - textWidth / 2f, y);
            y += bodyLayout.getHeight();
        }
        if (hasSig) {
            y += gap;
            drawLayout(c, sigLayout, cx - textWidth / 2f, y);
        }
        return usedSize;
    }

    private static StaticLayout layout(CharSequence text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build();
    }

    private static void drawLayout(Canvas c, StaticLayout l, float x, float y) {
        c.save();
        c.translate(x, y);
        l.draw(c);
        c.restore();
    }

    /* ---------------- decorations ---------------- */

    private static void dot(Canvas c, float x, float y, float r, int color, float s) {
        FILL.setColor(color);
        c.drawCircle(x * s, y * s, r * s, FILL);
    }

    private static void ring(Canvas c, float x, float y, float r, int color, float width, float s) {
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setColor(color);
        LINE.setStrokeWidth(width * s);
        c.drawCircle(x * s, y * s, r * s, LINE);
    }

    private static void drawDecoration(Canvas c, float w, float h, float s, Design d, int textColor) {
        switch (d.deco) {
            case D_CONFETTI: {
                final float[][] p = {{30, 40, 6}, {262, 56, 5}, {52, 352, 7}, {248, 340, 6}, {18, 200, 4},
                        {284, 214, 4}, {150, 18, 4}, {96, 26, 3}, {208, 380, 4}, {24, 120, 3}, {278, 130, 3}};
                final int[] cols = {0xFFF2C14E, 0xFFE2557A, 0xFF4FB28F, 0xFFF6A54B, 0xFFFFFFFF};
                for (int i = 0; i < p.length; i++) {
                    dot(c, p[i][0], p[i][1], p[i][2], cols[i % cols.length], s);
                }
                break;
            }
            case D_FRAME: {
                LINE.setStyle(Paint.Style.STROKE);
                LINE.setColor(d.text);
                LINE.setStrokeWidth(2f * s);
                RECT.set(14f * s, 14f * s, w - 14f * s, h - 14f * s);
                c.drawRect(RECT, LINE);
                LINE.setStrokeWidth(1f * s);
                RECT.set(21f * s, 21f * s, w - 21f * s, h - 21f * s);
                c.drawRect(RECT, LINE);
                break;
            }
            case D_BALLOONS: {
                final float[][] b = {{60, 50, 24, 30}, {150, 36, 21, 27}, {236, 56, 22, 28}};
                final int[] cols = {0xFFE2557A, 0xFF8A5BD6, 0xFFF2C14E};
                LINE.setStyle(Paint.Style.STROKE);
                LINE.setColor(0x66000000);
                LINE.setStrokeWidth(1f * s);
                for (int i = 0; i < 3; i++) {
                    FILL.setColor(cols[i]);
                    RECT.set((b[i][0] - b[i][2]) * s, (b[i][1] - b[i][3]) * s, (b[i][0] + b[i][2]) * s, (b[i][1] + b[i][3]) * s);
                    c.drawOval(RECT, FILL);
                    c.drawLine(b[i][0] * s, (b[i][1] + b[i][3]) * s, b[i][0] * s, (b[i][1] + b[i][3] + 26) * s, LINE);
                }
                break;
            }
            case D_CLOUDS: {
                FILL.setColor(0xFFFFFFFF);
                RECT.set(20f * s, 34f * s, 90f * s, 58f * s);
                c.drawOval(RECT, FILL);
                RECT.set(210f * s, 54f * s, 290f * s, 84f * s);
                c.drawOval(RECT, FILL);
                RECT.set(10f * s, 330f * s, 80f * s, 354f * s);
                c.drawOval(RECT, FILL);
                break;
            }
            case D_SNOW: {
                final float[][] p = {{30, 30, 3}, {250, 44, 2.5f}, {60, 120, 3}, {270, 160, 2}, {24, 230, 2.5f},
                        {262, 280, 3}, {70, 350, 2.5f}, {220, 372, 3}, {150, 16, 2}, {120, 388, 2}, {284, 90, 2}};
                for (float[] q : p) {
                    dot(c, q[0], q[1], q[2], 0xFFFFFFFF, s);
                }
                break;
            }
            case D_LIGHTS: {
                LINE.setStyle(Paint.Style.STROKE);
                LINE.setColor(0xFF6D7F8F);
                LINE.setStrokeWidth(2f * s);
                c.drawLine(14f * s, 18f * s, 286f * s, 18f * s, LINE);
                final int[] cols = {0xFFE2557A, 0xFFF2C14E, 0xFF4FB28F, 0xFF5AA6E8, 0xFFF6A54B};
                for (int i = 0; i < 5; i++) {
                    dot(c, 40 + i * 55, 28, 7, cols[i], s);
                }
                break;
            }
            case D_BOTTOM: {
                FILL.setColor(0xFFFFFFFF);
                RECT.set(0, h - 52f * s, w, h + 40f * s);
                c.drawRoundRect(RECT, 28f * s, 28f * s, FILL);
                break;
            }
            case D_GROUND: {
                FILL.setColor(0xFFFFFFFF);
                RECT.set(-40f * s, h - 46f * s, w + 40f * s, h + 60f * s);
                c.drawOval(RECT, FILL);
                break;
            }
            case D_FIREWORKS: {
                ring(c, 90, 80, 40, 0xFFE2557A, 2, s);
                ring(c, 220, 120, 28, 0xFFF2C14E, 2, s);
                ring(c, 70, 330, 22, 0xFF5FF0E0, 2, s);
                ring(c, 232, 330, 30, 0x99FFFFFF, 2, s);
                break;
            }
            case D_STARS: {
                final float[][] p = {{36, 36, 10}, {256, 52, 13}, {46, 352, 12}, {262, 332, 9}, {150, 16, 6}};
                for (float[] q : p) {
                    star(c, q[0] * s, q[1] * s, q[2] * s, q[2] * s * 0.42f, d.text);
                }
                break;
            }
            case D_GIFTS: {
                drawIcon(c, I_GIFT, 40f * s, 46f * s, 34f * s, 0xFFCFEADF, s);
                drawIcon(c, I_GIFT, 262f * s, 354f * s, 40f * s, 0xFFCFEADF, s);
                break;
            }
            case D_BUBBLES: {
                ring(c, 44, 56, 9, 0xFFFFFFFF, 2, s);
                ring(c, 250, 80, 6, 0xFFFFFFFF, 2, s);
                ring(c, 60, 336, 12, 0xFFFFFFFF, 2, s);
                ring(c, 240, 304, 7, 0xFFFFFFFF, 2, s);
                ring(c, 160, 370, 5, 0xFFFFFFFF, 2, s);
                break;
            }
            case D_FLOWERS: {
                drawIcon(c, I_FLOWER, 36f * s, 46f * s, 28f * s, d.iconColor, s);
                drawIcon(c, I_FLOWER, 268f * s, 66f * s, 22f * s, d.iconColor, s);
                drawIcon(c, I_FLOWER, 40f * s, 352f * s, 24f * s, d.iconColor, s);
                drawIcon(c, I_FLOWER, 262f * s, 344f * s, 30f * s, d.iconColor, s);
                break;
            }
            case D_RAYS: {
                LINE.setStyle(Paint.Style.STROKE);
                LINE.setColor(0x66FFFFFF);
                LINE.setStrokeWidth(4f * s);
                for (int a = 20; a <= 160; a += 20) {
                    double r = Math.toRadians(a);
                    c.drawLine(150f * s, 0, 150f * s + (float) Math.cos(r) * 190f * s, (float) Math.sin(r) * 190f * s, LINE);
                }
                break;
            }
            case D_HEARTS: {
                drawIcon(c, I_HEART, 34f * s, 44f * s, 24f * s, 0x66FFFFFF, s);
                drawIcon(c, I_HEART, 266f * s, 70f * s, 18f * s, 0x66FFFFFF, s);
                drawIcon(c, I_HEART, 44f * s, 350f * s, 20f * s, 0x66FFFFFF, s);
                drawIcon(c, I_HEART, 258f * s, 340f * s, 28f * s, 0x66FFFFFF, s);
                break;
            }
            case D_FLAGBAND: {
                FILL.setColor(0xFF1EB4E8);
                c.drawRect(0, h - 36f * s, w, h - 28f * s, FILL);
                FILL.setColor(0xFFFFFFFF);
                c.drawRect(0, h - 28f * s, w, h - 20f * s, FILL);
                FILL.setColor(0xFF1EB53A);
                c.drawRect(0, h - 20f * s, w, h - 12f * s, FILL);
                break;
            }
            default:
                break;
        }
    }

    /* ---------------- rich animated scenes (art box 300 x 215 units) ---------------- */

    private static final Paint SP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path SP1 = new Path();
    private static final Path SP2 = new Path();
    private static final RectF SR = new RectF();

    private static void fillColor(int color) {
        SP.setStyle(Paint.Style.FILL);
        SP.setShader(null);
        SP.setColor(color);
    }

    private static void sCircle(Canvas c, float x, float y, float r, int color) {
        fillColor(color);
        c.drawCircle(x, y, r, SP);
    }

    private static void sOval(Canvas c, float l, float t, float r, float b, int color) {
        fillColor(color);
        SR.set(l, t, r, b);
        c.drawOval(SR, SP);
    }

    private static void sRound(Canvas c, float l, float t, float r, float b, float rad, int color) {
        fillColor(color);
        SR.set(l, t, r, b);
        c.drawRoundRect(SR, rad, rad, SP);
    }

    private static void sLine(Canvas c, float x1, float y1, float x2, float y2, float width, int color) {
        SP.setShader(null);
        SP.setStyle(Paint.Style.STROKE);
        SP.setStrokeCap(Paint.Cap.ROUND);
        SP.setStrokeWidth(width);
        SP.setColor(color);
        c.drawLine(x1, y1, x2, y2, SP);
        SP.setStrokeCap(Paint.Cap.BUTT);
        SP.setStyle(Paint.Style.FILL);
    }

    private static void sGlow(Canvas c, float x, float y, float r, int color) {
        SP.setStyle(Paint.Style.FILL);
        SP.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, SP);
        SP.setShader(null);
    }

    private static void sTriangle(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        SP1.reset();
        SP1.moveTo(x1, y1);
        SP1.lineTo(x2, y2);
        SP1.lineTo(x3, y3);
        SP1.close();
        fillColor(color);
        c.drawPath(SP1, SP);
    }

    private static void sFlower(Canvas c, float cx, float cy, float r, int petal, int center) {
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            sCircle(c, cx + (float) Math.cos(a) * r * 0.9f, cy + (float) Math.sin(a) * r * 0.9f, r * 0.75f, petal);
        }
        sCircle(c, cx, cy, r * 0.55f, center);
    }

    /** 0..1 slowly pulsing value; freq must be a whole number so the loop is seamless. */
    private static float tw(float t, float freq, float phase) {
        return 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (t * freq + phase));
    }

    private static int alphaOf(int color, float a) {
        return ColorUtils.setAlphaComponent(color, Math.max(0, Math.min(255, (int) (255 * a))));
    }

    private static void sBurst(Canvas c, float cx, float cy, float p, int color) {
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            float r1 = 8 + p * 26;
            float r2 = r1 + 10 * (1 - p) + 2;
            sLine(c, cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                    cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, 2.2f, alphaOf(color, 1 - p));
        }
    }

    private static void drawScene(Canvas c, float s, int scene, float t) {
        c.save();
        c.scale(s, s);
        final float a = (float) (2 * Math.PI * t);
        switch (scene) {
            case 1:
                sceneCake(c, a, t);
                break;
            case 2:
                sceneTree(c, a, t);
                break;
            case 3:
                sceneMoon(c, a, t);
                break;
            case 4:
                sceneSpring(c, a, t);
                break;
            case 5:
                sceneFlowers(c, a, t);
                break;
            case 6:
                sceneFlag(c, a, t);
                break;
            case 7:
                sceneBooks(c, a, t);
                break;
            default:
                sceneShield(c, a, t);
                break;
        }
        c.restore();
    }

    /** 1: birthday cake with flickering candles and floating balloons */
    private static void sceneCake(Canvas c, float a, float t) {
        sGlow(c, 150, 110, 110, 0x66FFD8A8);
        final float[] bx = {52, 248, 150};
        final float[] by = {72, 60, 26};
        final float[] rx = {20, 18, 15};
        final float[] ry = {26, 24, 20};
        final int[] bc = {0xFFF2C14E, 0xFF4FD1A8, 0xFFFF8FB0};
        for (int i = 0; i < 3; i++) {
            float off = (float) Math.sin(a + i * 2.1f) * 5f;
            sLine(c, bx[i], by[i] + ry[i] + off, bx[i] + (i == 0 ? 5 : -5), by[i] + ry[i] + off + 32, 1.2f, 0x99FFFFFF);
            sOval(c, bx[i] - rx[i], by[i] - ry[i] + off, bx[i] + rx[i], by[i] + ry[i] + off, bc[i]);
            sOval(c, bx[i] - rx[i] * 0.55f, by[i] - ry[i] * 0.65f + off, bx[i] - rx[i] * 0.15f, by[i] - ry[i] * 0.2f + off, 0x55FFFFFF);
        }
        sOval(c, 58, 196, 242, 212, 0x2E000000);
        sRound(c, 70, 160, 230, 198, 10, 0xFFF6A54B);
        sRound(c, 86, 126, 214, 164, 9, 0xFFFFD1E0);
        sRound(c, 104, 98, 196, 130, 8, 0xFFFFFFFF);
        for (int k = 0; k < 6; k++) {
            sOval(c, 90 + k * 23 - 6, 126, 90 + k * 23 + 6, 138 + (k % 2) * 6, 0xFFFFFFFF);
        }
        for (int k = 0; k < 4; k++) {
            sOval(c, 110 + k * 24 - 6, 98, 110 + k * 24 + 6, 108 + (k % 2) * 5, 0xFFFFD1E0);
        }
        for (int k = 0; k < 7; k++) {
            sCircle(c, 82 + k * 22, 180, 3.5f, 0xFFFFE3C2);
        }
        for (int i = 0; i < 3; i++) {
            float x = 124 + i * 26;
            sRound(c, x - 3.5f, 76, x + 3.5f, 100, 2, 0xFF4FB2E8);
            sGlow(c, x, 66, 15, alphaOf(0xFFFFD34D, 0.3f + 0.2f * tw(t, 3, i * 0.3f)));
            c.save();
            c.translate(x, 74);
            c.scale(1f, 1f + 0.16f * (float) Math.sin(a * 3 + i));
            sOval(c, -4.5f, -15, 4.5f, 0, 0xFFFFD34D);
            sOval(c, -2.2f, -9, 2.2f, 0, 0xFFFFF3B0);
            c.restore();
        }
    }

    /** 2: New Year tree with twinkling lights and fireworks */
    private static void sceneTree(Canvas c, float a, float t) {
        final float[][] st = {{30, 30}, {86, 18}, {262, 110}, {44, 114}, {176, 20}, {272, 40}, {20, 80}};
        for (int i = 0; i < st.length; i++) {
            sCircle(c, st[i][0], st[i][1], 1.8f, alphaOf(0xFFFFFFFF, 0.25f + 0.75f * tw(t, 2, i * 0.17f)));
        }
        for (int b = 0; b < 2; b++) {
            float p = (t + b * 0.5f) % 1f;
            sBurst(c, b == 0 ? 238 : 60, b == 0 ? 60 : 96, p, b == 0 ? 0xFFFFD34D : 0xFFFF7AA8);
        }
        sGlow(c, 150, 70, 80, 0x55FFE9A0);
        sTriangle(c, 150, 34, 116, 86, 184, 86, 0xFF1C8A5A);
        sTriangle(c, 150, 62, 104, 130, 196, 130, 0xFF16794D);
        sTriangle(c, 150, 100, 94, 176, 206, 176, 0xFF126A42);
        sRound(c, 141, 174, 159, 190, 2, 0xFF6B4423);
        sOval(c, 40, 184, 260, 210, 0xFFE8F1FF);
        final float sc = 1f + 0.12f * (float) Math.sin(a * 2);
        star(c, 150, 26, 13 * sc, 5.5f * sc, 0xFFFFD34D);
        final float[][] o = {{132, 76}, {164, 100}, {120, 124}, {176, 140}, {150, 110}, {104, 166}, {196, 166}, {150, 152}};
        final int[] oc = {0xFFE2557A, 0xFFFFD34D, 0xFF5AB0F0, 0xFFE2557A, 0xFFFFFFFF, 0xFFFFD34D, 0xFF5AB0F0, 0xFFE2557A};
        for (int i = 0; i < o.length; i++) {
            sCircle(c, o[i][0], o[i][1], 4.5f, alphaOf(oc[i], 0.45f + 0.55f * tw(t, 2, i * 0.13f)));
        }
    }

    /** 3: Hayit: crescent moon, swinging lanterns and a mosque with glowing windows */
    private static void sceneMoon(Canvas c, float a, float t) {
        final float[][] st = {{34, 100}, {112, 30}, {262, 120}, {70, 140}, {176, 70}, {20, 50}};
        for (int i = 0; i < st.length; i++) {
            sCircle(c, st[i][0], st[i][1], 1.8f, alphaOf(0xFFF2D27A, 0.25f + 0.75f * tw(t, 2, i * 0.19f)));
        }
        sGlow(c, 214, 54, 70, 0x44F2D27A);
        SP1.reset();
        SP1.addCircle(212, 54, 30, Path.Direction.CW);
        SP2.reset();
        SP2.addCircle(224, 46, 26, Path.Direction.CW);
        SP1.op(SP2, Path.Op.DIFFERENCE);
        fillColor(0xFFF2D27A);
        c.drawPath(SP1, SP);
        sparkle(c, 176, 34, 6f + 2f * tw(t, 2, 0f), 0xFFF2D27A);
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 44 : 98;
            float len = i == 0 ? 36 : 20;
            c.save();
            c.translate(x, 0);
            c.rotate((float) Math.sin(a + i * 1.4f) * 7f);
            sLine(c, 0, 0, 0, len, 1.4f, 0xFFD9B24A);
            sRound(c, -8, len, 8, len + 5, 2, 0xFFD9B24A);
            sRound(c, -10, len + 5, 10, len + 35, 8, i == 0 ? 0xFFF2C14E : 0xFFE2557A);
            sGlow(c, 0, len + 20, 16, alphaOf(0xFFFFF4B0, 0.35f + 0.2f * tw(t, 2, i * 0.3f)));
            sRound(c, -8, len + 35, 8, len + 40, 2, 0xFFD9B24A);
            c.restore();
        }
        final int dark = 0xFF052D2A;
        SP1.reset();
        SP1.moveTo(104, 176);
        SP1.quadTo(150, 96, 196, 176);
        SP1.close();
        fillColor(dark);
        c.drawPath(SP1, SP);
        sLine(c, 150, 104, 150, 92, 1.6f, dark);
        sCircle(c, 150, 90, 2.6f, 0xFFF2C14E);
        sRound(c, 72, 116, 84, 178, 2, dark);
        sTriangle(c, 72, 116, 78, 98, 84, 116, dark);
        sRound(c, 216, 116, 228, 178, 2, dark);
        sTriangle(c, 216, 116, 222, 98, 228, 116, dark);
        fillColor(dark);
        c.drawRect(0, 174, 300, 215, SP);
        for (int i = 0; i < 4; i++) {
            float x = 124 + i * 15;
            sRound(c, x, 150, x + 6, 164, 3, alphaOf(0xFFFFD34D, 0.4f + 0.6f * tw(t, 2, i * 0.25f)));
        }
        sRound(c, 75, 128, 81, 140, 3, alphaOf(0xFFFFD34D, 0.4f + 0.6f * tw(t, 2, 0.1f)));
        sRound(c, 219, 128, 225, 140, 3, alphaOf(0xFFFFD34D, 0.4f + 0.6f * tw(t, 2, 0.6f)));
    }

    /** 4: Navro'z: rotating sun, drifting clouds, hills and swaying tulips */
    private static void sceneSpring(Canvas c, float a, float t) {
        sGlow(c, 150, 64, 90, 0x66FFE066);
        for (int i = 0; i < 2; i++) {
            float x = ((t * 380f + i * 190f) % 380f) - 60f;
            float y = i == 0 ? 40 : 82;
            sOval(c, x, y, x + 60, y + 18, 0xDDFFFFFF);
            sOval(c, x + 14, y - 9, x + 44, y + 13, 0xDDFFFFFF);
        }
        c.save();
        c.translate(150, 64);
        c.rotate(30f * t);
        for (int k = 0; k < 12; k++) {
            c.save();
            c.rotate(k * 30f);
            sLine(c, 0, -38, 0, -54, 4f, 0xFFFFE066);
            c.restore();
        }
        c.restore();
        sCircle(c, 150, 64, 30, 0xFFFFD34D);
        sCircle(c, 150, 64, 22, 0xFFFFE680);
        sOval(c, -70, 150, 200, 270, 0xFF3DAA6E);
        sOval(c, 110, 160, 380, 280, 0xFF2E8B6E);
        final float[] fx = {52, 112, 188, 246};
        final int[] fc = {0xFFE2557A, 0xFFFFFFFF, 0xFFF6A54B, 0xFFFF8FB0};
        for (int i = 0; i < 4; i++) {
            c.save();
            c.translate(fx[i], 200);
            c.rotate((float) Math.sin(a + i * 1.3f) * 4f);
            sLine(c, 0, 0, 0, -34, 3f, 0xFF1F7A4D);
            sOval(c, 1, -26, 13, -18, 0xFF1F7A4D);
            sFlower(c, 0, -40, 7f, fc[i], 0xFFFFE066);
            c.restore();
        }
    }

    /** 5: 8-mart: a bouquet that sways and hearts floating up */
    private static void sceneFlowers(Canvas c, float a, float t) {
        sGlow(c, 150, 100, 110, 0x44FFFFFF);
        final float[] fx = {78, 150, 224};
        final float[] fy = {112, 84, 118};
        final float[] fr = {13, 17, 12};
        final int[] pc = {0xFFFFFFFF, 0xFFFFD1E0, 0xFFF6A54B};
        final int[] cc = {0xFFFFD34D, 0xFFE2557A, 0xFFFFF3C4};
        for (int i = 0; i < 3; i++) {
            c.save();
            c.translate(fx[i], 210);
            c.rotate((float) Math.sin(a + i * 1.6f) * 4f);
            float h = 210 - fy[i];
            sLine(c, 0, 0, 0, -h, 4f, 0xFF2F9E6E);
            sOval(c, 2, -h * 0.5f - 8, 24, -h * 0.5f + 6, 0xFF2F9E6E);
            sFlower(c, 0, -h, fr[i], pc[i], cc[i]);
            c.restore();
        }
        for (int i = 0; i < 4; i++) {
            float p = (t + i * 0.25f) % 1f;
            float x = 40 + i * 72 + (float) Math.sin(a + i) * 6f;
            float y = 190 - p * 170f;
            float al = p < 0.15f ? p / 0.15f : (p > 0.8f ? (1 - p) / 0.2f : 1f);
            drawIcon(c, I_HEART, x, y, 14 + (i % 2) * 6, alphaOf(0xFFFFFFFF, 0.85f * al), 1f);
        }
    }

    private static float wave(float x, float x0, float x1, float a) {
        return (float) (4.2 * Math.sin(2 * Math.PI * (x - x0) / 110.0 - a)) * ((x - x0) / (x1 - x0));
    }

    /** 6: Mustaqillik: a waving flag on a pole with twinkling sparkles */
    private static void sceneFlag(Canvas c, float a, float t) {
        sGlow(c, 150, 100, 110, 0x33FFFFFF);
        sOval(c, 40, 198, 260, 214, 0x33000000);
        sRound(c, 66, 34, 71, 208, 2, 0xFFC9CCD6);
        sCircle(c, 68.5f, 32, 4.5f, 0xFFF2C14E);
        final float x0 = 71;
        final float x1 = 236;
        final float top = 46;
        final float h = 34;
        final int[] bcols = {0xFF1EB4E8, 0xFFFFFFFF, 0xFF1EB53A};
        for (int b = 0; b < 3; b++) {
            SP1.reset();
            for (float x = x0; x <= x1; x += 6) {
                float y = top + b * h + wave(x, x0, x1, a);
                if (x == x0) {
                    SP1.moveTo(x, y);
                } else {
                    SP1.lineTo(x, y);
                }
            }
            for (float x = x1; x >= x0; x -= 6) {
                SP1.lineTo(x, top + (b + 1) * h + wave(x, x0, x1, a));
            }
            SP1.close();
            fillColor(bcols[b]);
            c.drawPath(SP1, SP);
        }
        for (int b = 1; b <= 2; b++) {
            float off = b == 1 ? 2f : -2f;
            for (float x = x0; x < x1; x += 6) {
                sLine(c, x, top + b * h + off + wave(x, x0, x1, a), x + 6, top + b * h + off + wave(x + 6, x0, x1, a),
                        1.4f, 0xFFCE1126);
            }
        }
        final float mw = wave(96, x0, x1, a);
        SP1.reset();
        SP1.addCircle(94, top + 17 + mw, 10, Path.Direction.CW);
        SP2.reset();
        SP2.addCircle(98, top + 15 + mw, 8.4f, Path.Direction.CW);
        SP1.op(SP2, Path.Op.DIFFERENCE);
        fillColor(0xFFFFFFFF);
        c.drawPath(SP1, SP);
        for (int r = 0; r < 3; r++) {
            for (int k = 0; k < 4; k++) {
                float x = 116 + k * 11;
                sCircle(c, x, top + 8 + r * 9 + wave(x, x0, x1, a), 1.6f, 0xFFFFFFFF);
            }
        }
        sparkle(c, 40, 54, 5f + 3f * tw(t, 2, 0f), 0xFFF2D27A);
        sparkle(c, 264, 76, 5f + 3f * tw(t, 2, 0.3f), 0xFFF2D27A);
        sparkle(c, 256, 170, 5f + 3f * tw(t, 2, 0.6f), 0xFFF2D27A);
        sparkle(c, 36, 150, 5f + 3f * tw(t, 2, 0.8f), 0xFFF2D27A);
    }

    /** 7: O'qituvchilar kuni: stack of books, an apple, a pencil and sparkles */
    private static void sceneBooks(Canvas c, float a, float t) {
        sGlow(c, 150, 110, 100, 0x44FFFFFF);
        sOval(c, 60, 192, 240, 212, 0x2E000000);
        sRound(c, 66, 166, 234, 194, 5, 0xFF2B7BD6);
        sRound(c, 66, 166, 78, 194, 5, 0xFF1D4F86);
        sRound(c, 84, 172, 228, 188, 3, 0xFFF3F6FA);
        sRound(c, 82, 140, 222, 168, 5, 0xFFE2557A);
        sRound(c, 82, 140, 94, 168, 5, 0xFFA82A55);
        sRound(c, 100, 146, 216, 162, 3, 0xFFFFF0F4);
        sRound(c, 96, 116, 206, 142, 5, 0xFFF2C14E);
        sRound(c, 96, 116, 108, 142, 5, 0xFFC9962A);
        sRound(c, 114, 122, 200, 136, 3, 0xFFFFF7DD);
        final float bob = (float) Math.sin(a) * 2f;
        sOval(c, 128, 88 + bob, 172, 124 + bob, 0xFFE23A3A);
        sOval(c, 136, 94 + bob, 146, 104 + bob, 0x55FFFFFF);
        sLine(c, 150, 88 + bob, 152, 80 + bob, 2.2f, 0xFF6B4423);
        sOval(c, 152, 74 + bob, 168, 84 + bob, 0xFF3DAA6E);
        c.save();
        c.translate(250, 140);
        c.rotate(24f);
        sRound(c, -6, -90, 6, 20, 2, 0xFFFFD34D);
        sRound(c, -6, -98, 6, -88, 2, 0xFFE2557A);
        sTriangle(c, -6, 20, 6, 20, 0, 36, 0xFFF3D1A0);
        sTriangle(c, -2, 31, 2, 31, 0, 36, 0xFF2A2A2A);
        c.restore();
        sparkle(c, 60, 60, 5f + 3f * tw(t, 2, 0f), 0xFFFFFFFF);
        sparkle(c, 250, 44, 5f + 3f * tw(t, 2, 0.4f), 0xFFFFF3B0);
        sparkle(c, 208, 100, 4f + 3f * tw(t, 2, 0.7f), 0xFFFFFFFF);
        sparkle(c, 40, 120, 4f + 3f * tw(t, 2, 0.2f), 0xFFFFF3B0);
    }

    /** 8: Vatan himoyachilari: a shield with a star, laurel branches and small fireworks */
    private static void sceneShield(Canvas c, float a, float t) {
        final float pulse = 0.5f + 0.5f * (float) Math.sin(a);
        final float[][] st = {{30, 120}, {270, 130}, {100, 24}, {206, 20}, {16, 190}, {286, 190}};
        for (int i = 0; i < st.length; i++) {
            sCircle(c, st[i][0], st[i][1], 1.8f, alphaOf(0xFFF2D27A, 0.25f + 0.75f * tw(t, 2, i * 0.17f)));
        }
        sBurst(c, 46, 50, t % 1f, 0xFFFFD34D);
        sBurst(c, 254, 56, (t + 0.5f) % 1f, 0xFF5FF0E0);
        sGlow(c, 150, 108, 100 + 10 * pulse, alphaOf(0xFFF2D27A, 0.3f + 0.15f * pulse));
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < 7; k++) {
                double th = Math.toRadians(110 + k * 22);
                float x = 150 + 80f * (float) Math.cos(th) * -side;
                float y = 112 + 80f * (float) Math.sin(th);
                c.save();
                c.translate(x, y);
                c.rotate((float) Math.toDegrees(th) * -side + 90f * -side);
                sOval(c, -9, -3.5f, 9, 3.5f, 0xFF4FB28F);
                c.restore();
            }
        }
        SP1.reset();
        SP1.moveTo(150, 50);
        SP1.lineTo(194, 66);
        SP1.lineTo(194, 104);
        SP1.cubicTo(194, 134, 174, 152, 150, 164);
        SP1.cubicTo(126, 152, 106, 134, 106, 104);
        SP1.lineTo(106, 66);
        SP1.close();
        fillColor(0xFF1B3A6B);
        c.drawPath(SP1, SP);
        SP.setStyle(Paint.Style.STROKE);
        SP.setStrokeWidth(4f);
        SP.setStrokeJoin(Paint.Join.ROUND);
        SP.setColor(0xFFF2D27A);
        c.drawPath(SP1, SP);
        SP.setStyle(Paint.Style.FILL);
        final float sc = 1f + 0.08f * pulse;
        star(c, 150, 106, 24 * sc, 10 * sc, 0xFFF2D27A);
    }

    /* ---------------- icons (24 x 24 grid) ---------------- */

    private static void star(Canvas c, float cx, float cy, float outer, float inner, int color) {
        PATH.reset();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                PATH.moveTo(px, py);
            } else {
                PATH.lineTo(px, py);
            }
        }
        PATH.close();
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(PATH, FILL);
    }

    private static void sparkle(Canvas c, float cx, float cy, float outer, int color) {
        PATH.reset();
        final float inner = outer * 0.3f;
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 4;
            float r = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                PATH.moveTo(px, py);
            } else {
                PATH.lineTo(px, py);
            }
        }
        PATH.close();
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        c.drawPath(PATH, FILL);
    }

    private static void drawIcon(Canvas c, int type, float cx, float cy, float size, int color, float s) {
        final float g = size / 24f;
        final float ox = cx - size / 2f;
        final float oy = cy - size / 2f;
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
        LINE.setStrokeWidth(1.8f * g);
        LINE.setColor(color);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setColor(color);
        switch (type) {
            case I_CAKE:
                c.drawLine(ox + 3 * g, oy + 21 * g, ox + 21 * g, oy + 21 * g, LINE);
                RECT.set(ox + 5 * g, oy + 12 * g, ox + 19 * g, oy + 21 * g);
                c.drawRoundRect(RECT, 2 * g, 2 * g, LINE);
                c.drawLine(ox + 5 * g, oy + 16 * g, ox + 19 * g, oy + 16 * g, LINE);
                c.drawLine(ox + 12 * g, oy + 12 * g, ox + 12 * g, oy + 8.5f * g, LINE);
                c.drawCircle(ox + 12 * g, oy + 6 * g, 1.6f * g, FILL);
                break;
            case I_SPARKLE:
                sparkle(c, ox + 11 * g, oy + 13 * g, 10 * g, color);
                sparkle(c, ox + 19.5f * g, oy + 4.5f * g, 3.6f * g, color);
                sparkle(c, ox + 4.5f * g, oy + 19.5f * g, 3f * g, color);
                break;
            case I_STAR:
                star(c, ox + 12 * g, oy + 12.8f * g, 10.5f * g, 4.4f * g, color);
                break;
            case I_GIFT:
                RECT.set(ox + 4 * g, oy + 11 * g, ox + 20 * g, oy + 21 * g);
                c.drawRect(RECT, LINE);
                RECT.set(ox + 3 * g, oy + 7.5f * g, ox + 21 * g, oy + 11 * g);
                c.drawRect(RECT, LINE);
                c.drawLine(ox + 12 * g, oy + 7.5f * g, ox + 12 * g, oy + 21 * g, LINE);
                c.drawCircle(ox + 9.2f * g, oy + 5 * g, 2.5f * g, LINE);
                c.drawCircle(ox + 14.8f * g, oy + 5 * g, 2.5f * g, LINE);
                break;
            case I_CROWN:
                PATH.reset();
                PATH.moveTo(ox + 3 * g, oy + 19 * g);
                PATH.lineTo(ox + 3 * g, oy + 8 * g);
                PATH.lineTo(ox + 8 * g, oy + 13 * g);
                PATH.lineTo(ox + 12 * g, oy + 5.5f * g);
                PATH.lineTo(ox + 16 * g, oy + 13 * g);
                PATH.lineTo(ox + 21 * g, oy + 8 * g);
                PATH.lineTo(ox + 21 * g, oy + 19 * g);
                PATH.close();
                c.drawPath(PATH, FILL);
                c.drawLine(ox + 3 * g, oy + 21.5f * g, ox + 21 * g, oy + 21.5f * g, LINE);
                break;
            case I_BALLOON:
                RECT.set(ox + 6 * g, oy + 2 * g, ox + 18 * g, oy + 17 * g);
                c.drawOval(RECT, FILL);
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 16.5f * g);
                PATH.lineTo(ox + 10.6f * g, oy + 19 * g);
                PATH.lineTo(ox + 13.4f * g, oy + 19 * g);
                PATH.close();
                c.drawPath(PATH, FILL);
                LINE.setStrokeWidth(1.2f * g);
                c.drawLine(ox + 12 * g, oy + 19 * g, ox + 12 * g, oy + 23 * g, LINE);
                break;
            case I_SNOWFLAKE:
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI / 3 * i;
                    float dx = (float) Math.cos(a) * 10 * g;
                    float dy = (float) Math.sin(a) * 10 * g;
                    c.drawLine(ox + 12 * g - dx, oy + 12 * g - dy, ox + 12 * g + dx, oy + 12 * g + dy, LINE);
                }
                c.drawCircle(ox + 12 * g, oy + 12 * g, 2 * g, FILL);
                break;
            case I_TREE:
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 2.5f * g);
                PATH.lineTo(ox + 6.5f * g, oy + 10 * g);
                PATH.lineTo(ox + 17.5f * g, oy + 10 * g);
                PATH.close();
                PATH.moveTo(ox + 12 * g, oy + 7 * g);
                PATH.lineTo(ox + 5 * g, oy + 15 * g);
                PATH.lineTo(ox + 19 * g, oy + 15 * g);
                PATH.close();
                PATH.moveTo(ox + 12 * g, oy + 11 * g);
                PATH.lineTo(ox + 4 * g, oy + 20 * g);
                PATH.lineTo(ox + 20 * g, oy + 20 * g);
                PATH.close();
                c.drawPath(PATH, FILL);
                RECT.set(ox + 10.5f * g, oy + 20 * g, ox + 13.5f * g, oy + 23 * g);
                c.drawRect(RECT, FILL);
                break;
            case I_BELL:
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 4.5f * g);
                PATH.cubicTo(ox + 7.5f * g, oy + 4.5f * g, ox + 7 * g, oy + 9 * g, ox + 7 * g, oy + 12 * g);
                PATH.cubicTo(ox + 7 * g, oy + 15 * g, ox + 5.5f * g, oy + 16 * g, ox + 4.5f * g, oy + 18 * g);
                PATH.lineTo(ox + 19.5f * g, oy + 18 * g);
                PATH.cubicTo(ox + 18.5f * g, oy + 16 * g, ox + 17 * g, oy + 15 * g, ox + 17 * g, oy + 12 * g);
                PATH.cubicTo(ox + 17 * g, oy + 9 * g, ox + 16.5f * g, oy + 4.5f * g, ox + 12 * g, oy + 4.5f * g);
                PATH.close();
                c.drawPath(PATH, FILL);
                c.drawCircle(ox + 12 * g, oy + 3.5f * g, 1.4f * g, FILL);
                c.drawCircle(ox + 12 * g, oy + 20.6f * g, 1.9f * g, FILL);
                break;
            case I_SNOWMAN:
                c.drawCircle(ox + 12 * g, oy + 16.5f * g, 5.5f * g, LINE);
                c.drawCircle(ox + 12 * g, oy + 7.5f * g, 3.8f * g, LINE);
                c.drawCircle(ox + 10.9f * g, oy + 6.8f * g, 0.7f * g, FILL);
                c.drawCircle(ox + 13.1f * g, oy + 6.8f * g, 0.7f * g, FILL);
                c.drawCircle(ox + 12 * g, oy + 15 * g, 0.8f * g, FILL);
                c.drawCircle(ox + 12 * g, oy + 18 * g, 0.8f * g, FILL);
                break;
            case I_CLOCK:
                c.drawCircle(ox + 12 * g, oy + 12 * g, 9.5f * g, LINE);
                c.drawLine(ox + 12 * g, oy + 12 * g, ox + 12 * g, oy + 6.5f * g, LINE);
                c.drawLine(ox + 12 * g, oy + 12 * g, ox + 16 * g, oy + 14.5f * g, LINE);
                break;
            case I_MOON:
                PATH.reset();
                PATH.addCircle(ox + 11f * g, oy + 12.5f * g, 8.5f * g, Path.Direction.CW);
                PATH2.reset();
                PATH2.addCircle(ox + 15.2f * g, oy + 9.8f * g, 7.2f * g, Path.Direction.CW);
                PATH.op(PATH2, Path.Op.DIFFERENCE);
                c.drawPath(PATH, FILL);
                sparkle(c, ox + 19f * g, oy + 5f * g, 3f * g, color);
                break;
            case I_FLOWER:
                for (int i = 0; i < 5; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                    c.drawCircle(ox + 12 * g + (float) Math.cos(a) * 5.2f * g, oy + 12 * g + (float) Math.sin(a) * 5.2f * g, 3.8f * g, FILL);
                }
                FILL.setColor(0xFFFFE066);
                c.drawCircle(ox + 12 * g, oy + 12 * g, 2.6f * g, FILL);
                break;
            case I_SUN:
                c.drawCircle(ox + 12 * g, oy + 12 * g, 4.6f * g, FILL);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(ox + 12 * g + (float) Math.cos(a) * 7.4f * g, oy + 12 * g + (float) Math.sin(a) * 7.4f * g,
                            ox + 12 * g + (float) Math.cos(a) * 10.5f * g, oy + 12 * g + (float) Math.sin(a) * 10.5f * g, LINE);
                }
                break;
            case I_HEART:
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 21 * g);
                PATH.cubicTo(ox + 3 * g, oy + 14.5f * g, ox + 2.5f * g, oy + 8 * g, ox + 7 * g, oy + 6 * g);
                PATH.cubicTo(ox + 9.5f * g, oy + 4.8f * g, ox + 11.5f * g, oy + 6.2f * g, ox + 12 * g, oy + 8 * g);
                PATH.cubicTo(ox + 12.5f * g, oy + 6.2f * g, ox + 14.5f * g, oy + 4.8f * g, ox + 17 * g, oy + 6 * g);
                PATH.cubicTo(ox + 21.5f * g, oy + 8 * g, ox + 21 * g, oy + 14.5f * g, ox + 12 * g, oy + 21 * g);
                PATH.close();
                c.drawPath(PATH, FILL);
                break;
            case I_BOOK:
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 6 * g);
                PATH.lineTo(ox + 4 * g, oy + 4.5f * g);
                PATH.lineTo(ox + 4 * g, oy + 18.5f * g);
                PATH.lineTo(ox + 12 * g, oy + 20 * g);
                PATH.close();
                PATH.moveTo(ox + 12 * g, oy + 6 * g);
                PATH.lineTo(ox + 20 * g, oy + 4.5f * g);
                PATH.lineTo(ox + 20 * g, oy + 18.5f * g);
                PATH.lineTo(ox + 12 * g, oy + 20 * g);
                PATH.close();
                c.drawPath(PATH, LINE);
                break;
            case I_FLAG:
                c.drawLine(ox + 5 * g, oy + 3 * g, ox + 5 * g, oy + 22 * g, LINE);
                FILL.setColor(0xFF1EB4E8);
                c.drawRect(ox + 5 * g, oy + 4 * g, ox + 20 * g, oy + 7.4f * g, FILL);
                FILL.setColor(0xFFFFFFFF);
                c.drawRect(ox + 5 * g, oy + 7.4f * g, ox + 20 * g, oy + 10.8f * g, FILL);
                FILL.setColor(0xFF1EB53A);
                c.drawRect(ox + 5 * g, oy + 10.8f * g, ox + 20 * g, oy + 14.2f * g, FILL);
                break;
            case I_SHIELD:
                PATH.reset();
                PATH.moveTo(ox + 12 * g, oy + 2.5f * g);
                PATH.lineTo(ox + 20 * g, oy + 5.5f * g);
                PATH.lineTo(ox + 20 * g, oy + 12 * g);
                PATH.cubicTo(ox + 20 * g, oy + 17 * g, ox + 16.5f * g, oy + 20 * g, ox + 12 * g, oy + 22 * g);
                PATH.cubicTo(ox + 7.5f * g, oy + 20 * g, ox + 4 * g, oy + 17 * g, ox + 4 * g, oy + 12 * g);
                PATH.lineTo(ox + 4 * g, oy + 5.5f * g);
                PATH.close();
                c.drawPath(PATH, LINE);
                star(c, ox + 12 * g, oy + 12.4f * g, 4.8f * g, 2f * g, color);
                break;
            default:
                break;
        }
    }
}
