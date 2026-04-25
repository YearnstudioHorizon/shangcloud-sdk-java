package cn.yearnstudio.shangcloud;

import java.util.concurrent.ConcurrentHashMap;

public class RamKv implements TempVarStorage {
    private final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();

    @Override
    public void setTempVariable(String key, String value) {
        storage.put(key, value);
    }

    @Override
    public String getTempVariable(String key) throws ShangCloudException {
        String value = storage.get(key);
        if (value == null) {
            throw new ShangCloudException("Key '" + key + "' not found");
        }
        return value;
    }

    @Override
    public void deleteTempVariable(String key) {
        storage.remove(key);
    }
}
