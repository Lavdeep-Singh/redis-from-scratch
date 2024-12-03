import java.util.HashMap;
import java.util.Map;

public class Config {
    private final Map<String, String> configMap = new HashMap<>();

        public void setConfig(String key, String value) {
            configMap.put(key, value);
        }

        public String getConfig(String key) {
            return configMap.getOrDefault(key, null);
    }
}
