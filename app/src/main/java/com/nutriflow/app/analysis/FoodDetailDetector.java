package com.nutriflow.app.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative recognition of a single food detail, never a clicked list item. */
public final class FoodDetailDetector {
    private static final String[] FOODS = {
            "米饭", "盖饭", "盖浇饭", "炒饭", "拌饭", "焖饭", "煲仔饭", "饭团",
            "拉面", "拌面", "炒面", "汤面", "烩面", "凉面", "刀削面", "热干面", "意面",
            "米线", "米粉", "河粉", "螺蛳粉", "酸辣粉", "肠粉", "粉丝", "粉条",
            "牛肉", "牛排", "羊肉", "猪肉", "鸡肉", "鸡腿", "鸡胸", "鸡翅", "鸡排",
            "鸭肉", "烤鸭", "鸭腿", "卤鸭", "排骨", "五花肉", "里脊", "肥牛", "香肠",
            "烤肠", "腊肉", "叉烧", "红烧肉", "烧鸡", "黄焖鸡", "宫保鸡丁", "口水鸡",
            "酸菜鱼", "烤鱼", "鱼肉", "鱼片", "三文鱼", "鳕鱼", "带鱼", "鲈鱼", "鲫鱼",
            "虾仁", "龙虾", "对虾", "白虾", "海鲜", "螃蟹", "蟹肉", "鱿鱼", "蛤蜊", "生蚝",
            "鸡蛋", "鸭蛋", "煎蛋", "卤蛋", "茶叶蛋", "蒸蛋", "蛋羹", "皮蛋",
            "豆腐", "豆干", "豆皮", "腐竹", "西兰花", "生菜", "白菜", "青菜", "菠菜",
            "土豆", "番茄", "西红柿", "茄子", "黄瓜", "南瓜", "玉米", "菌菇", "香菇",
            "沙拉", "蔬菜", "时蔬", "汉堡", "鸡腿堡", "牛肉堡", "披萨", "寿司", "炸鸡",
            "薯条", "薯饼", "三明治", "饺子", "水饺", "馄饨", "抄手", "包子", "小笼包",
            "生煎包", "馒头", "烧麦", "煎饼", "手抓饼", "肉夹馍", "麻辣烫", "麻辣香锅",
            "烧烤", "烤串", "火锅", "砂锅粥", "小米粥", "白粥", "八宝粥", "瘦肉粥",
            "绿豆汤", "紫菜汤", "鸡汤", "鱼汤", "排骨汤", "奶茶", "咖啡", "豆浆", "牛奶",
            "酸奶", "果汁", "柠檬茶", "可乐", "汽水", "矿泉水", "面包", "蛋糕", "甜品",
            "冰淇淋", "雪糕", "布丁", "蛋挞", "饼干", "巧克力", "水果", "苹果", "香蕉",
            "草莓", "橙子", "葡萄", "西瓜", "芒果", "菠萝", "坚果", "花生", "核桃",
            "卤肉饭", "鸡柳", "鸡米花", "原味鸡", "手枪腿", "鸡肉卷", "鸡腿卷", "肉卷",
            "油条", "烤冷面", "炒饼", "葱油饼", "酱香饼", "锅盔", "馕", "肉饼", "锅贴",
            "小面", "担担面", "牛腩", "肥肠", "肉末", "肉沫", "鸭血", "毛肚", "鱼丸",
            "肉丸", "贡丸", "豆花", "藕片", "莲藕", "土豆丝", "土豆片", "花菜", "炒蛋",
            "双皮奶", "酸梅汤", "冰粉", "杨枝甘露", "柠檬水", "乌龙茶", "红茶", "绿茶", "口味虾"
    };
    private static final String[] NON_FOOD = {
            "手机", "电脑", "耳机", "手机壳", "iphone", "ipad", "macbook", "airpods", "watch",
            "充电", "面膜", "面霜", "洗面", "粉底", "散粉",
            "口红", "洗发", "沐浴", "护肤", "香水", "清洁", "洗衣", "纸巾", "卫生", "垃圾",
            "玩具", "模型", "抱枕", "钥匙扣", "衣服", "短袖", "羽绒", "鞋", "皮包", "皮革",
            "猫粮", "狗粮", "宠物", "饲料", "猫罐头", "狗罐头", "钓饵", "鱼饵", "餐具",
            "电饭", "炒锅", "煎锅", "奶茶杯", "咖啡杯", "咖啡机", "豆浆机", "榨汁机", "料理机",
            "切菜", "刀具", "盘子", "收纳", "洗手", "饭碗", "瓷碗", "沙拉碗套装",
            "药品", "胶囊", "药片", "面馆", "饭店", "餐厅", "旗舰店", "专卖店", "门店",
            "店铺", "附近", "配送", "起送", "满减", "优惠券", "代金券", "团购券", "兑换券",
            "搜索", "请输入", "猜你喜欢", "为你推荐", "大家都在搜", "营养流", "营养分析",
            "联系电话", "营业时间", "评价", "回购"
    };
    private static final String[] DETAIL = {
            "商品详情", "菜品详情", "餐品详情", "美食详情", "商品描述", "商品介绍", "菜品介绍",
            "图文详情", "套餐详情", "商品信息", "主要原料", "主要食材"
    };
    private static final String[] BUY = {"加入购物车", "加入餐车", "加入购物袋", "加入购物车按钮",
            "立即购买", "立即抢购", "立即下单", "添加到购物车", "买它",
            "加购", "选好了", "选好啦"};
    private static final Pattern PRICE = Pattern.compile("(?:[¥￥]\\s*|(?:售价|到手价|价格|现价)[:： ]*)(\\d{1,4}(?:\\.\\d{1,2})?)(?![\\d.])");
    private static final Pattern YUAN = Pattern.compile("^(\\d{1,4}(?:\\.\\d{1,2})?)\\s*元(?:/份|起)?$");
    private static final Pattern SALES = Pattern.compile("(?:月售|已售|销量|好评率|好评)\\s*[:：]?\\s*[\\d.万千+%％]+(?:份|件|单|人)?");

    private FoodDetailDetector() { }

    public static boolean isFoodName(String value) {
        if (value == null) return false;
        String name = cleanTitle(value).toLowerCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 70 || containsAny(name, NON_FOOD)) return false;
        if (name.matches(".*\\d{2}:\\d{2}.*") || name.contains("http")) return false;
        return containsAny(name, FOODS);
    }

    public static Result detect(List<String> visibleLines) {
        if (visibleLines == null || visibleLines.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        // Some apps expose an entire title/card as one content-description node.
        // Bound the input and split metadata without using clicked text as a fallback.
        int characters = 0;
        outer: for (String raw : visibleLines) {
            if (raw == null) continue;
            characters += raw.length();
            if (characters > 30000) return null;
            for (String part : raw.split("[\\n\\r|•]+")) {
                String line = part.trim();
                if (isRelatedSection(line)) break outer;
                if (!line.isEmpty() && !lines.contains(line)) lines.add(line);
            }
        }
        StringBuilder page = new StringBuilder();
        for (String line : lines) page.append(line).append('\n');
        String text = page.toString();
        if (containsAny(text, "筛选", "综合排序", "搜索发现", "附近商家")
                || (text.contains("点菜") && text.contains("商家"))) return null;

        boolean explicitDetail = containsAny(text, DETAIL)
                || (hasControl(lines, "详情") && hasControl(lines, "商品", "评价"));
        boolean specs = containsAny(text, "选择规格", "规格选择", "已选规格", "请选择规格", "选择口味")
                || (text.contains("已选") && containsAny(text, "规格", "口味"));
        boolean close = hasControl(lines, "关闭", "关闭详情", "关闭弹窗", "关闭商品详情", "关闭按钮", "关闭对话框", "×", "✕");
        boolean back = hasControl(lines, "返回", "返回按钮", "返回上一页");
        boolean buy = containsAny(text, BUY) || hasControl(lines, "加入", "选购");
        boolean plus = hasControl(lines, "+", "＋", "增加", "增加数量", "添加商品", "添加");
        boolean configuration = containsAny(text, "数量", "购买数量", "商品评价", "菜品评价", "商品评分", "已选", "口味", "规格")
                || (hasControl(lines, "客服", "收藏", "分享") && hasControl(lines, "购物车", "店铺"));

        Map<String, String> titles = new LinkedHashMap<>();
        int titleIndex = -1;
        // Food choices below a specification/ingredient header aren't extra products.
        boolean afterTitleSection = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean section = isEvidenceHeader(line) || isOptionsHeader(line)
                    || containsAny(line, "商品评价", "用户评价", "商品评分");
            if (section) {
                if (!titles.isEmpty()) afterTitleSection = true;
                continue;
            }
            if (afterTitleSection || !isFoodName(line)) continue;
            String name = cleanTitle(line);
            if (name.isEmpty()) continue;
            if (titles.isEmpty()) titleIndex = i;
            titles.put(identity(name), name);
        }
        if (titles.size() != 1) return null;
        String name = titles.values().iterator().next();
        double price = priceNearTitle(lines, titleIndex);
        boolean priced = !Double.isNaN(price);
        // A closeable food sheet can lack a literal "商品详情" heading. A full
        // page without that heading additionally needs back + configuration.
        // A price, a food name, or a click alone is NEVER sufficient.
        boolean detailContext = explicitDetail || specs
                || (priced && (close || (back && configuration)));
        boolean purchase = buy || (plus && priced && (explicitDetail || specs || close));
        if (!detailContext || !purchase) return null;

        StringBuilder evidence = new StringBuilder(name);
        int ingredientLines = 0;
        boolean reviews = false;
        for (int i = titleIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (containsAny(line, "商品评价", "用户评价", "买家评价")) reviews = true;
            if (reviews) continue;
            if (containsAny(line, "配送", "售后", "加入购物", "立即购买", "价格") || isOptionsHeader(line)) ingredientLines = 0;
            if (isEvidenceHeader(line)) {
                evidence.append('\n').append(line);
                ingredientLines = 6;
            } else if (ingredientLines > 0) {
                evidence.append('\n').append(line);
                ingredientLines--;
            }
        }
        return new Result(name, evidence.toString(), price, close);
    }

    private static boolean isEvidenceHeader(String line) {
        return containsAny(line, "配料", "原料", "食材", "商品描述", "商品介绍", "菜品介绍");
    }

    private static boolean isOptionsHeader(String line) {
        return line.matches("^(?:(?:请选择|选择|已选)(?:规格|口味|份量|分量|加料|配菜|温度|甜度|辣度).*|(?:规格|规格选择|口味|份量|分量|加料|配菜|温度|甜度|辣度|套餐内容|套餐包含)(?:[:：].*)?)$");
    }

    private static boolean isRelatedSection(String line) {
        return line.matches("^(?:猜你喜欢|看了又看|相似商品|商家推荐|为你推荐|相关推荐|搭配推荐)(?:\\s.*|[：:].*)?$");
    }

    private static boolean hasControl(List<String> lines, String... controls) {
        for (String line : lines) for (String control : controls) if (line.equals(control)) return true;
        return false;
    }

    private static double priceNearTitle(List<String> lines, int titleIndex) {
        for (int i = titleIndex; i < Math.min(lines.size(), titleIndex + 14); i++) {
            String line = lines.get(i);
            if (i > titleIndex && (isOptionsHeader(line) || isEvidenceHeader(line))) break;
            if (containsAny(line, "配送", "包装", "起送", "满减", "原价", "划线价", "优惠券", "加价")) continue;
            Matcher m = PRICE.matcher(line);
            if (m.find()) return Double.parseDouble(m.group(1));
            Matcher yuan = YUAN.matcher(line);
            if (yuan.matches()) return Double.parseDouble(yuan.group(1));
            if ((line.equals("¥") || line.equals("￥")) && i + 1 < lines.size()
                    && lines.get(i + 1).matches("\\d{1,4}(?:\\.\\d{1,2})?")) {
                return Double.parseDouble(lines.get(i + 1));
            }
        }
        return Double.NaN;
    }

    private static String cleanTitle(String text) {
        String value = SALES.matcher(text).replaceAll("");
        value = value.replaceFirst("^(?:商品名称|菜品名称|餐品名称)[:：]\\s*", "");
        // Keep the food part of a combined accessible title/price/button node.
        value = value.replaceAll("[¥￥]\\s*\\d+(?:\\.\\d+)?", "");
        value = value.replaceFirst("(?:加入购物车|加入餐车|加入购物袋|添加到购物车|立即购买|立即抢购|立即下单|加购|选规格|选好了|选好啦|买它).*$", "");
        return value.replaceAll("\\s+", "").replaceAll("^[，,;；·|]+|[，,;；·|]+$", "").trim();
    }

    public static String identity(String name) {
        return cleanTitle(name).replaceAll("[【\\[](?:招牌|热销|推荐|优惠)[】\\]]", "")
                .replaceAll("[（(]?(?:大|小|中)?[份碗][）)]?$", "");
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    public static final class Result {
        public final String foodName;
        public final String evidence;
        public final double price;
        public final boolean dismissible;
        private Result(String foodName, String evidence, double price, boolean dismissible) {
            this.foodName = foodName; this.evidence = evidence; this.price = price;
            this.dismissible = dismissible;
        }
    }
}
