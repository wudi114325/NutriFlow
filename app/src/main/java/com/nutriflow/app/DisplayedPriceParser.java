package com.nutriflow.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses visible dish prices, preserving the relationship to a price label. */
final class DisplayedPriceParser {
    private static final String NUMBER = "(\\d{1,4}(?:\\.\\d{1,2})?)(?![\\d.])";
    private static final String GAP = "[\\s·:：]*";
    private static final Pattern LABEL = Pattern.compile("一口价|单件到手价|到手价|券后价|折后价|实付价|支付价|预计价");
    private static final Pattern AFTER = Pattern.compile("^" + GAP + "(?:[¥￥]\\s*)?" + NUMBER);
    private static final Pattern BEFORE = Pattern.compile("[¥￥]\\s*" + NUMBER + "(?:元)?" + GAP + "$");
    private static final Pattern BEFORE_YUAN = Pattern.compile(NUMBER + "元[，,\\s]*$");
    private static final Pattern SYMBOL = Pattern.compile("[¥￥]\\s*" + NUMBER);

    static double parse(String clicked, String page) {
        String visible = normalize(page);
        String click = normalize(clicked);
        double labeled = labeledPrice(visible);
        if (labeled > 0) return labeled;
        labeled = labeledPrice(click);
        if (labeled > 0) return labeled;
        // Prefer the current page to an earlier click event during navigation.
        for (String source : new String[] {visible, click}) {
            Matcher symbol = SYMBOL.matcher(source);
            while (symbol.find()) {
                double amount = Double.parseDouble(symbol.group(1));
                if (amount > 0) return amount;
            }
        }
        return 25.0; // Existing estimate used when the page exposes no price.
    }

    private static double labeledPrice(String source) {
        Matcher label = LABEL.matcher(source);
        double result = -1;
        int best = -1;
        while (label.find()) {
            String prefix = source.substring(Math.max(0, label.start() - 16), label.start());
            // A membership/first-order offer is not the ordinary dish price.
            if (prefix.matches("(?s).*(?:会员|月卡|专享|首单|新客)[^·\\n]{0,8}$")) continue;
            Matcher before = BEFORE.matcher(prefix);
            Matcher beforeYuan = BEFORE_YUAN.matcher(prefix);
            Matcher after = AFTER.matcher(source.substring(label.end()));
            boolean hasSymbolBefore = before.find();
            boolean hasYuanBefore = beforeYuan.find();
            boolean hasBefore = hasSymbolBefore || hasYuanBefore;
            boolean hasAfter = after.find();
            if (!hasBefore && !hasAfter) continue;
            // JD renders '¥11.99 单件到手价 ¥25.99': the label describes
            // the preceding number, while the following number is list price.
            boolean suffix = hasBefore && (!hasAfter || label.group().equals("单件到手价"));
            String precedingAmount = hasSymbolBefore ? before.group(1)
                    : hasYuanBefore ? beforeYuan.group(1) : null;
            double value = Double.parseDouble(suffix ? precedingAmount : after.group(1));
            int score = label.group().equals("一口价") ? 30 : 20;
            if (label.group().equals("预计价")) score = 10;
            if (value > 0 && score > best) { result = value; best = score; }
        }
        return result;
    }

    private static String normalize(String source) {
        if (source == null) return "";
        return source.replaceAll("(?<=[¥￥])\\s*·\\s*(?=\\d)", "")
                .replaceAll("(?<=\\d)\\s*·\\s*(?=\\.\\d)", "")
                .replaceAll("(?<=\\d)\\s*·\\s*\\.\\s*·\\s*(?=\\d)", ".");
    }
}
