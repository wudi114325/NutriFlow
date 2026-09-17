import com.nutriflow.app.analysis.DetailVisitTracker;
import com.nutriflow.app.analysis.FoodDetailDetector;
import com.nutriflow.app.analysis.MealNutritionAnalyzer;
import java.util.Arrays;

/** Run with a JDK; does not require Android or a live delivery account. */
public final class RecognitionRegression {
    private static int checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static FoodDetailDetector.Result page(String... lines) {
        return FoodDetailDetector.detect(Arrays.asList(lines));
    }
    public static void main(String[] args) {
        // Listing/home/store screens seen in the recording must not open a sheet.
        check(page("首页", "炸鸡汉堡", "¥11", "配送费", "选规格") == null, "home is not detail");
        check(page("附近商家", "牛肉盖饭", "¥26", "商品详情", "加入购物车") == null, "restaurant list");
        check(page("点菜", "评价", "商家", "鸡腿饭", "商品详情", "加入购物车") == null, "store menu");
        check(page("商品详情", "牛肉盖饭", "¥20", "番茄炒蛋", "¥15", "加入购物车") == null, "multiple products");
        check(page("综合排序", "商品详情", "牛肉盖饭", "¥20", "加入购物车") == null, "search results");
        check(page("规格", "配送费", "¥3", "加入购物车") == null, "generic page no food fallback");
        check(page("鸡腿饭") == null, "clicked name alone");
        check(page("商品详情", "牛肉盖饭", "¥20") == null, "unfinished page");
        for (String title : new String[]{"保湿面霜", "珍珠散粉", "手机", "牛皮鞋", "鸡肉狗粮",
                "牛肉宠物零食", "草莓奶茶杯", "汉堡玩具", "苹果手机", "苹果 iPhone 17", "咖啡机",
                "豆浆机", "面馆", "当前餐品", "商品详情"}) {
            check(!FoodDetailDetector.isFoodName(title), "non-food rejected: " + title);
            check(page("商品详情", title, "¥20", "立即购买") == null, "non-food detail: " + title);
        }
        for (String title : new String[]{"香辣鸡腿堡", "番茄炒蛋盖饭", "螺蛳粉", "皮蛋瘦肉粥", "西兰花",
                "无糖豆浆", "水果沙拉", "烤肠", "【优惠】土豆炖牛肉盖浇饭"}) {
            FoodDetailDetector.Result r = page("返回", "商品详情", title, "¥19.90", "加入购物车");
            check(r != null && r.foodName.equals(title), "food detail: " + title);
            check(r.price == 19.90, "meal price: " + title);
        }
        FoodDetailDetector.Result detail = page("商品详情", "牛肉盖饭", "配送费 ¥3", "¥26.90", "原料：牛肉、米饭", "加入购物车",
                "猜你喜欢", "花生奶茶", "¥12");
        check(detail != null && detail.price == 26.90, "ignore delivery fee");
        check(!detail.evidence.contains("花生"), "related food not used as allergy evidence");
        check(detail.evidence.contains("牛肉"), "keep ingredient evidence");
        check(page("商品详情", "牛肉盖饭", "¥20", "配料：", "花生", "加入购物车").evidence.contains("花生"), "split ingredient nodes");
        check(page("选择规格", "香辣鸡腿堡", "¥12", "规格", "加入购物车") != null, "food specification sheet");
        detail = page("商品详情", "鸡腿饭", "配送费 ¥3", "加入购物车");
        check(detail != null && Double.isNaN(detail.price), "no fabricated price");
        check(page("商品详情", "鸡腿饭", "￥", "12.5", "加入购物车").price == 12.5, "split price nodes");
        check(page("商品详情", "鸡腿饭", "12元", "加入购物车").price == 12, "yuan price");
        MealNutritionAnalyzer.Result analysis = MealNutritionAnalyzer.analyze("鸡腿饭", "鸡腿饭", Double.NaN, 500, "无", "清淡", "25");
        check(analysis.getRecommendationPrice() == 0, "unknown price does not create budget allowance");
        check(analysis.getRecommendations().get(0).contains("未识别"), "unknown price explanation");

        DetailVisitTracker visits = new DetailVisitTracker();
        check(!visits.observe("A", 1000), "initial detail waits");
        check(!visits.observe("A", 1300), "animation not stable");
        check(visits.observe("A", 1700), "stable detail opens");
        visits.markShown("A");
        check(!visits.observe("A", 3000), "content update or dismissal does not reopen");
        visits.observe("", 3100);
        visits.observe("A", 3200);
        check(!visits.observe("A", 4000), "transient missing nodes do not reopen");
        visits.observe("", 4100);
        visits.observe("", 5100);
        check(!visits.observe("A", 5200), "return visit stabilizes again");
        check(visits.observe("A", 5900), "new visit opens");
        visits.markShown("A");
        check(!visits.observe("B", 6000), "different food waits");
        check(visits.observe("B", 6700), "different food opens");
        visits.markShown("B");
        visits.reset();
        check(!visits.observe("B", 7000), "reenable does not show stale detail immediately");
        check(visits.observe("B", 7700), "reenable can analyze current detail");
        System.out.println("PASS: " + checks + " recognition regression checks");
    }
}
