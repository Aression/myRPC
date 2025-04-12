package alg;

public class SimpleHashMap<K, V> {
    private static final int DEFAULT_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.8f;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    @SuppressWarnings("unchecked")
    private Node<K, V>[] table = (Node<K, V>[]) new Node[DEFAULT_CAPACITY];
    private int size;
    private int threshold;
    private final float loadFactor;

    static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    public SimpleHashMap() {
        this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public SimpleHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
        // 初始化为最小2的幂次容量
        this.table = (Node<K, V>[]) new Node[this.threshold];
        this.threshold = (int) (this.threshold * loadFactor);
    }

    private int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    public V put(K key, V value) {
        int hash = hash(key);
        int index = indexFor(hash, table.length);
        Node<K, V> node = table[index];

        // 检查键是否已存在
        while (node != null) {
            if (node.hash == hash && (key == node.key || key.equals(node.key))) {
                V oldValue = node.value;
                node.value = value;
                return oldValue;
            }
            node = node.next;
        }

        // 插入新节点到链表头部
        addNode(hash, key, value, index);

        // 检查是否需要扩容（全局阈值判断）
        if (size > threshold) {
            resize();
        }

        return null;
    }

    private void addNode(int hash, K key, V value, int index) {
        Node<K, V> newNode = new Node<>(hash, key, value, table[index]);
        table[index] = newNode;
        size++;
        System.out.println("添加键值对 -> 键: " + key + ", 哈希值: " + hash + ", 索引: " + index + ", 当前大小: " + size + ", 阈值: " + threshold);
        
        // 打印当前桶的链表情况
        if (newNode.next != null) {
            System.out.println("  发生哈希冲突! 当前桶[" + index + "]的链表结构:");
            Node<K, V> current = newNode;
            int position = 0;
            while (current != null) {
                System.out.println("    [" + position++ + "] -> 键: " + current.key + ", 哈希值: " + current.hash);
                current = current.next;
            }
        }
    }

    private void resize() {
        System.out.println("\n===== 开始扩容 =====");
        System.out.println("当前容量: " + table.length + ", 新容量: " + (table.length << 1) + ", 新阈值: " + (int)((table.length << 1) * loadFactor));
        
        int oldCapacity = table.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity > MAXIMUM_CAPACITY) {
            throw new RuntimeException("Exceed maximum capacity");
        }

        @SuppressWarnings("unchecked")
        Node<K, V>[] newTable = (Node<K, V>[]) new Node[newCapacity];
        this.threshold = (int) (newCapacity * loadFactor);
        System.out.println("开始迁移节点...");

        // 迁移节点到新数组（优化为高低位桶）
        for (int i = 0; i < oldCapacity; i++) {
            Node<K, V> e = table[i];
            if (e == null) continue;

            Node<K, V> loHead = null, loTail = null;
            Node<K, V> hiHead = null, hiTail = null;

            while (e != null) {
                Node<K, V> next = e.next;
                if ((e.hash & oldCapacity) == 0) { // 低位桶（原位置）
                    if (loTail == null) {
                        loHead = e;
                    } else {
                        loTail.next = e;
                    }
                    loTail = e;
                } else { // 高位桶（原位置 + oldCapacity）
                    if (hiTail == null) {
                        hiHead = e;
                    } else {
                        hiTail.next = e;
                    }
                    hiTail = e;
                }
                e = next;
            }

            // 放入新数组
            if (loTail != null) {
                loTail.next = null;
                newTable[i] = loHead;
                System.out.println("  桶[" + i + "] -> 保持原位置的节点: " + printNodeKeys(loHead));
            }
            if (hiTail != null) {
                hiTail.next = null;
                newTable[i + oldCapacity] = hiHead;
                System.out.println("  桶[" + (i + oldCapacity) + "] -> 移动到新位置的节点: " + printNodeKeys(hiHead));
            }
        }

        table = newTable;
        System.out.println("===== 扩容完成 =====\n");
    }

    public V get(K key) {
        int hash = hash(key);
        int index = indexFor(hash, table.length);
        Node<K, V> node = table[index];
        
        while (node != null) {
            if (node.hash == hash && (key == node.key || key.equals(node.key))) {
                return node.value;
            }
            node = node.next;
        }
        return null;
    }

    private int hash(K key) {
        if (key == null) return 0;
        int h = key.hashCode();
        return h ^ (h >>> 16); // 高位异或低位
    }

    private int indexFor(int hash, int length) {
        return hash & (length - 1); // 确保length是2的幂次
    }

    public int size() {
        return size;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===== SimpleHashMap状态 =====\n");
        sb.append("容量: ").append(table.length).append(", 元素数量: ").append(size)
          .append(", 阈值: ").append(threshold).append(", 负载因子: ").append(loadFactor).append("\n");
        
        sb.append("哈希表内部结构:\n");
        for (int i = 0; i < table.length; i++) {
            Node<K, V> node = table[i];
            if (node != null) {
                sb.append("  桶[" + i + "]: ");
                while (node != null) {
                    sb.append(node.key).append("(哈希值=" + node.hash + ")")
                      .append(node.next != null ? " -> " : "");
                    node = node.next;
                }
                sb.append("\n");
            }
        }
        sb.append("===== 状态结束 =====\n");
        return sb.toString();
    }
    
    // 辅助方法：打印链表中所有节点的键
    private String printNodeKeys(Node<K, V> head) {
        if (head == null) return "空";
        StringBuilder sb = new StringBuilder();
        Node<K, V> current = head;
        while (current != null) {
            sb.append(current.key);
            if (current.next != null) sb.append(" -> ");
            current = current.next;
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("===== SimpleHashMap演示 =====\n");
        System.out.println("创建一个默认容量(16)和负载因子(0.8)的哈希表，阈值为: 16 * 0.8 = 12.8，向下取整为12\n");
        
        // 创建哈希表
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        
        // 第一组测试：演示基本插入
        System.out.println("1. 基本插入操作演示");
        String[] basicData = {"A", "B", "C", "D"};
        for (String key : basicData) {
            System.out.println("\n插入键: " + key);
            map.put(key, key.hashCode());
        }
        
        // 第二组测试：演示哈希冲突 - 在Java中，"Aa"和"BB"的hashCode相同
        System.out.println("\n2. 哈希冲突演示");
        String[] collisionData = {"Aa", "BB"};
        System.out.println("\n插入可能发生哈希冲突的键: Aa和BB");
        System.out.println("Aa的hashCode: " + "Aa".hashCode());
        System.out.println("BB的hashCode: " + "BB".hashCode());
        
        for (String key : collisionData) {
            map.put(key, key.hashCode());
        }
        
        // 第三组测试：演示扩容
        System.out.println("\n3. 扩容演示 - 继续添加元素直到触发扩容");
        String[] moreData = {"E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "AA", "AB", "AC", "AD", "AE", "AF", "AG", "AH", "AI", "AJ", "AK", "AL", "AM", "AN", "AO", "AP", "AQ", "AR", "AS", "AT", "AU", "AV", "AW", "AX", "AY", "AZ"};
        for (String key : moreData) {
            System.out.println("\n插入键: " + key);
            map.put(key, key.hashCode());
        }
        
        // 第四组测试：演示查询
        System.out.println("\n4. 查询操作演示");
        String[] keysToGet = {"A", "Aa", "BB", "Z"};
        for (String key : keysToGet) {
            Integer value = map.get(key);
            System.out.println("查询键 '" + key + "': " + (value != null ? "找到，值为" + value : "未找到"));
        }
        
        // 打印最终哈希表状态
        System.out.println(map.toString());
        
        System.out.println("===== 演示结束 =====");
    }
}