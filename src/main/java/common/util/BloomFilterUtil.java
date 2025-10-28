package common.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 布隆过滤器工具类，用于防止缓存穿透
 */
public class BloomFilterUtil {
    private static final Logger logger = LoggerFactory.getLogger(BloomFilterUtil.class);
    
    // 存储不同业务的布隆过滤器
    private static final ConcurrentHashMap<String, BloomFilter<String>> FILTER_MAP = new ConcurrentHashMap<>();
    
    // 默认预期插入量
    private static final int DEFAULT_EXPECTED_INSERTIONS = 10000;
    // 默认误判率
    private static final double DEFAULT_FPP = 0.001;
    
    /**
     * 获取指定业务的布隆过滤器
     * 
     * @param business 业务名称
     * @return 布隆过滤器实例
     */
    public static BloomFilter<String> getFilter(String business) {
        return FILTER_MAP.computeIfAbsent(business, k -> createFilter(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP));
    }
    
    /**
     * 创建布隆过滤器
     * 
     * @param expectedInsertions 预期插入量
     * @param fpp 误判率
     * @return 布隆过滤器实例
     */
    public static BloomFilter<String> createFilter(int expectedInsertions, double fpp) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );
    }
    
    /**
     * 向指定业务的布隆过滤器添加元素
     * 
     * @param business 业务名称
     * @param item 要添加的元素
     * @return 是否添加成功
     */
    public static boolean add(String business, String item) {
        BloomFilter<String> filter = getFilter(business);
        boolean result = filter.put(item);
        // logger.debug("添加到布隆过滤器: business={}, item={}, result={}", business, item, result);
        return result;
    }
    
    /**
     * 判断元素是否可能存在于指定业务的布隆过滤器中
     * 
     * @param business 业务名称
     * @param item 要判断的元素
     * @return 元素是否可能存在（true可能存在，false一定不存在）
     */
    public static boolean mightContain(String business, String item) {
        BloomFilter<String> filter = getFilter(business);
        boolean result = filter.mightContain(item);
        // logger.debug("布隆过滤器检查: business={}, item={}, result={}", business, item, result);
        return result;
    }
    
    /**
     * 重置指定业务的布隆过滤器
     * 
     * @param business 业务名称
     */
    public static void reset(String business) {
        FILTER_MAP.put(business, createFilter(DEFAULT_EXPECTED_INSERTIONS, DEFAULT_FPP));
        logger.info("重置布隆过滤器: business={}", business);
    }
    
    /**
     * 重置指定业务的布隆过滤器，并指定预期插入量和误判率
     * 
     * @param business 业务名称
     * @param expectedInsertions 预期插入量
     * @param fpp 误判率
     */
    public static void reset(String business, int expectedInsertions, double fpp) {
        FILTER_MAP.put(business, createFilter(expectedInsertions, fpp));
        logger.info("重置布隆过滤器: business={}, expectedInsertions={}, fpp={}", business, expectedInsertions, fpp);
    }
} 