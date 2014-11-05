package cn.shoudle.smack;

public class SdException extends Exception {
	private static final long serialVersionUID = 1L;

	public SdException(String message) {
		super(message);
	}

	public SdException(String message, Throwable cause) {
		super(message, cause);
	}
}
