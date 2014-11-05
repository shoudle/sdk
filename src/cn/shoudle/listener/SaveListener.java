package cn.shoudle.listener;

public abstract class SaveListener {

	public SaveListener(){
		
	}
	
	public abstract void onSuccess();
	public abstract void onFailure(String errorMessage);
	
}
