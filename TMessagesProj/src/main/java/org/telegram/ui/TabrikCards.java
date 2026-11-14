package org.telegram.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

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

        Design(String name, int c1, int c2, int mode, int deco, int icon, int iconColor, int text, int font, int big) {
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

    private static final Cat[] CATS = {
            // ---------------- Tug'ilgan kun ----------------
            new Cat("Tug'ilgan kun", "Tug'ilgan kuningiz muborak!", 0xFFE2557A, 0xFFF6A54B, NAMES_FUN, new String[]{
                    "Aziz {to}! Sizni tug'ilgan kuningiz bilan chin qalbimdan tabriklayman. Sizga sog'lik, baxt, oilaviy farovonlik va barcha orzularingiz ushalishini tilayman. Hayotingiz yorqin va mazmunli bo'lsin!",
                    "Hurmatli {to}! Tug'ilgan kuningiz munosabati bilan sizni samimiy tabriklaymiz. Sizga mustahkam sog'lik, ishlaringizda muvaffaqiyat va oila baxti tilaymiz.",
                    "{to}, tug'ilgan kuningiz muborak! Doim baxtli va sog' bo'ling!",
                    "{to}, yoshingiz yana bir yoshga ulg'aydi, aqlingiz ham shunga yarasha o'ssin! Tug'ilgan kuningiz bilan, tortdan esa menga ham ulush qoldiring!"
            }, new Design[]{
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
        StaticLayout titleLayout = layout(t, titlePaint, (int) textWidth);

        final float regionTop = (d.deco == D_BALLOONS ? 100f : 30f) * s + cardPad;
        float bottomReserve = 30f;
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
        StaticLayout sigLayout = hasSig ? layout(sig, sigPaint, (int) textWidth) : null;
        final float sigH = hasSig ? gap + sigLayout.getHeight() : 0f;

        StaticLayout bodyLayout = null;
        float total = 0;
        for (float size = 14f; size >= 7.5f; size -= 0.5f) {
            bodyPaint.setTextSize(size * s);
            bodyLayout = hasBody ? layout(body, bodyPaint, (int) textWidth) : null;
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
