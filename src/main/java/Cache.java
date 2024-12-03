import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    private static final Map<String, String> data = new ConcurrentHashMap<>();
    private static final Map<String, Long> expirations = new ConcurrentHashMap<>();
    private static final Cache instance = new Cache();

    public static Cache getInstance() {
        return instance;
    }

    public String get(String key) {
        System.out.println("Getting value for key: " + key);
        return data.getOrDefault(key, null);
    }

    public void set(String key, String value) {
        System.out.println("Setting value for key: " + key);
        data.put(key, value);
    }

    public void remove(String key){
        System.out.println("Removing value for key: " + key);
        data.remove(key);
        expirations.remove(key);
    }

    public void setWithExpiry(String key, String value, Long expiry) {
        System.out.println("Setting value for key: " + key + " with expiry " + expiry);
        data.put(key, value);

        if (expiry > 0) {
            expirations.put(key, expiry);
        }
    }

    public Long getExpiryTime(String key) {
        Long expiry = expirations.get(key);
        System.out.println("Returning key: " + key + " with expiry " + expiry);
        return expiry;
    }
}
