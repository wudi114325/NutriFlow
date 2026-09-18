package com.nutriflow.app;

/** Standalone regression cases; run with javac/java, no Android device required. */
public final class DisplayedPriceParserTest {
    public static void main(String[] args) {
        check("", "一口价 · ¥ · 18 · .8 · ¥28.8", 18.8);
        check("", "一口价 · ¥ · 18 · . · 8 · ¥28.8", 18.8);
        check("", "¥11.99 · 单件到手价 · ¥25.99 · 月卡专享价11.75元 · 到手价¥11.99", 11.99);
        check("", "¥11.99 · 单件到手价 · ¥25.99", 11.99);
        check("", "¥25.99 · 到手价¥11.99 · 立即购买", 11.99);
        check("", "到手价：￥11.99 · 一口价：¥10.99", 10.99);
        check("", "月卡专享到手价¥9.9 · 到手价¥11.99", 11.99);
        check("¥25.99", "到手价¥11.99", 11.99);
        check("¥25.99", "¥11.99", 11.99);
        check("", "预计价¥9.3 · ¥13.8", 9.3);
        check("", "一口价规则说明 · 满20减5 · 到手价¥11.99", 11.99);
        check("", "¥10.3 · 鹅肉盖饭", 10.3);
        // Captured JD accessibility content descriptions (not visual guesses).
        check("", "9.88元，单件到手价 · ¥23.88 · 到手价¥9.88", 9.88);
        check("", "9.88元，单件到手价 · ¥ · 9 · .88 · 单件到手价 · ¥23.88", 9.88);
        check("", "11.99元，单件到手价 · ¥25.99", 11.99);
        check("", "9.88元，单件到手价 · ¥23.88 · 超级月卡专享价9.68元", 9.88);
        System.out.println("16 displayed-price regression cases passed");
    }

    private static void check(String clicked, String page, double expected) {
        double actual = DisplayedPriceParser.parse(clicked, page);
        if (Math.abs(actual - expected) > 0.001) {
            throw new AssertionError(page + ": expected " + expected + ", got " + actual);
        }
    }
}
