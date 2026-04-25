package cn.yearnstudio.shangcloud;

public interface TempVarStorage {
    void setTempVariable(String key, String value);
    String getTempVariable(String key) throws ShangCloudException;
    void deleteTempVariable(String key);
}
