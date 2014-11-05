package cn.shoudle.im;

import cn.shoudle.listener.SaveListener;
import cn.shoudle.service.SdService;
import cn.shoudle.v1.SdMessage;
import android.content.Context;
/**
 * 用户管理类;
 * @author Render;
 */
public class SdUserManager {

	private static Context mContext;
	private static SdUserManager instance;
	private static Object instance_lock;
	
	static{
		instance_lock=new Object();
	}
	
	public static SdUserManager getInstance(Context ct){

		synchronized (instance_lock) {
			mContext=ct;
			if(instance==null){
				instance=new SdUserManager();	
			}
			return instance;
		}
	}
	
	/**
	 * 用户登录;
	 */
	public void login(String userName,String password,SaveListener saveListener){
		
		SdService sdService=SdChat.getInstance(mContext).getService();
		
		if(sdService!=null){
			sdService.Login(userName, password,saveListener);
		}else {
			saveListener.onFailure(SdMessage.MSG_CORE_SERVICE_NULL);
		}
	}
	
	/**
	 * 用户注册;
	 * @param userName
	 * @param password
	 * @param saveListener
	 */
	public void register(String userName,String password,SaveListener saveListener){
		
		SdService sdService=SdChat.getInstance(mContext).getService();
		
		if(sdService!=null){
			sdService.register(userName, password, saveListener);
		}else {
			saveListener.onFailure(SdMessage.MSG_CORE_SERVICE_NULL);
		}
	}
	
	/**
	 * 用户退出;
	 */
	public void logout(){
	
		SdService sdService=SdChat.getInstance(mContext).getService();
		
		if(sdService!=null){
			sdService.logout();
		}
	}
}
