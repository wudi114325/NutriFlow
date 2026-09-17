package com.nutriflow.app.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
            "草莓", "橙子", "葡萄", "西瓜", "芒果", "菠萝", "坚果", "花生", "核桃"
    };
    private static final String[] NON_FOOD = {
            "手机", "电脑", "耳机", "手机壳", "iphone", "ipad", "macbook", "airpods", "watch",
            "充电", "面膜", "面霜", "洗面", "粉底", "散粉",
            "口红", "洗发", "沐浴", "护肤", "香水", "清洁", "洗衣", "纸巾", "卫生", "垃圾",
            "玩具", "模型", "抱枕", "钥匙扣", "衣服", "短袖", "羽绒", "鞋", "皮包", "皮革",
            "猫粮", "狗粮", "宠物", "饲料", "猫罐头", "狗罐头", "钓饵", "鱼饵", "餐具",
            "电饭", "炒锅", "煎锅", "奶茶杯", "咖啡杯", "咖啡机", "豆浆机", "榨汁机", "料理机",
            "切菜", "刀具", "碗", "盘子", "收纳", "洗手",
            "药品", "胶囊", "药片", "面馆", "饭店", "餐厅", "旗舰店", "专卖店", "门店",
            "店铺", "附近", "配送", "起送", "满减", "优惠券", "代金券", "团购券", "兑换券",
            "搜索", "请输入", "猜你喜欢", "为你推荐", "大家都在搜", "营养流", "营养分析",
            "联系电话", "营业时间", "已售", "月售", "销量", "评价", "好评", "回购", "招牌推荐"
    };
    private static final String[] DETAIL = {
            "商品详情", "菜品详情", "餐品详情", "美食详情", "商品描述", "商品介绍", "菜品介绍",
            "图文详情", "套餐详情", "商品信息", "主要原料", "主要食材"
    };
    private static final String[] BUY = {"加入购物车", "加入餐车", "加入购物袋", "立即购买", "立即抢购", "立即下单"};
    private static final Pattern PRICE = Pattern.compile("(?:[¥￥]\\s*|(?:售价|到手价|价格)[:： ]*)(\\d{1,4}(?:\\.\\d{1,2})?)(?![\\d.])");

    private FoodDetailDetector() { }

    public static boolean isFoodName(String value) {
        if (value == null) return false;
        String name = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 55 || containsAny(name, NON_FOOD)) return false;
        if (name.matches(".*\\d{2}:\\d{2}.*") || name.contains("http")) return false;
        return containsAny(name, FOODS);
    }

    public static Result detect(List<String> visibleLines) {
        if (visibleLines == null || visibleLines.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        for (String raw : visibleLines) {
            if (raw == null) continue;
            String line = raw.trim();
            // Related items must not supply the title, price or allergy evidence.
            if (containsAny(line, "猜你喜欢", "看了又看", "相似商品", "商家推荐", "为你推荐")) break;
            if (!line.isEmpty()) lines.add(line);
        }
        StringBuilder page = new StringBuilder();
        for (String line : lines) page.append(line).append('\n');
        String text = page.toString();
        boolean explicitDetail = containsAny(text, DETAIL);
        boolean specs = containsAny(text, "选择规格", "规格选择", "已选规格")
                || (text.contains("已选") && text.contains("规格"));
        if (!explicitDetail && !specs) return null;
        if (!containsAny(text, BUY)) return null;
        // A restaurant/menu/search page is not a single-product detail.
        if (containsAny(text, "筛选", "综合排序", "搜索发现", "附近商家")
                || (text.contains("点菜") && text.contains("商家"))) return null;

        Set<String> titles = new LinkedHashSet<>();
        int titleIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (containsAny(line, "配料", "原料", "食材", "商品描述", "商品介绍", "菜品介绍", "图文详情")) {
                // Ingredients/descriptions after the title are evidence, not more titles.
                if (!titles.isEmpty()) break;
                continue;
            }
            if (isFoodName(line)) {
                String name = cleanTitle(line);
                if (titles.isEmpty()) titleIndex = i;
                titles.add(name);
            }
        }
        if (titles.size() != 1) return null;
        String name = titles.iterator().next();
        // Only prices near the identified title; delivery fees never count as meal prices.
        double price = Double.NaN;
        for (int i = titleIndex; i < Math.min(lines.size(), titleIndex + 9); i++) {
            String line = lines.get(i);
            if (containsAny(line, "配送", "包装", "起送", "满减", "原价", "划线价", "优惠券")) continue;
            Matcher m = PRICE.matcher(line);
            if (m.find()) { price = Double.parseDouble(m.group(1)); break; }
            Matcher yuan = Pattern.compile("^(\\d{1,4}(?:\\.\\d{1,2})?)\\s*元(?:起)?$").matcher(line);
            if (yuan.matches()) { price = Double.parseDouble(yuan.group(1)); break; }
            if ((line.equals("¥") || line.equals("￥")) && i + 1 < lines.size()
                    && lines.get(i + 1).matches("\\d{1,4}(?:\\.\\d{1,2})?")) {
                price = Double.parseDouble(lines.get(i + 1)); break;
            }
        }
        // Nutrition needs a known food; missing prices are shown as unknown, never invented.
        StringBuilder evidence = new StringBuilder(name);
        int ingredientLines = 0;
        for (int i = titleIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (containsAny(line, "评价", "配送", "售后", "加入购物", "立即购买", "价格")) ingredientLines = 0;
            if (containsAny(line, "配料", "原料", "食材", "口味", "辣", "糖", "含乳", "含蛋")) {
                evidence.append('\n').append(line);
                if (containsAny(line, "配料", "原料", "食材")) ingredientLines = 6;
            } else if (ingredientLines > 0) {
                evidence.append('\n').append(line);
                ingredientLines--;
            }
        }
        return new Result(name, evidence.toString(), price);
    }

    private static String cleanTitle(String text) {
        return text.replaceAll("[¥￥]\\s*\\d+(?:\\.\\d+)?", "").trim();
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    public static final class Result {
        public final String foodName;
        public final String evidence;
        public final double price;
        private Result(String foodName, String evidence, double price) {
            this.foodName = foodName; this.evidence = evidence; this.price = price;
        }
    }
}
