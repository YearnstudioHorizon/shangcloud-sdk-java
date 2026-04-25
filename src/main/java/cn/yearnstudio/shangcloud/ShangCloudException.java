package cn.yearnstudio.shangcloud;

public class ShangCloudException extends Exception {
    public ShangCloudException(String message) {
        super(message);
    }

    public ShangCloudException(String message, Throwable cause) {
        super(message, cause);
    }
}
