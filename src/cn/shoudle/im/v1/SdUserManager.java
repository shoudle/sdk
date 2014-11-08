package cn.shoudle.im.v1;

import cn.shoudle.listener.SaveListener;
import cn.shoudle.push.SdService;
import cn.shoudle.util.SdLog;
import android.content.Context;
/**
 * 用户管理类;
 * @author Render;
 */
public class SdUserManager {

	private static Context mContext;
	private static SdUserManager instance;
	
	public synchronized static SdUserManager getInstance(Context ct){

		mContext=ct;
		
		if(ct==null){
			SdLog.i("the context is null");
		}
		
		if(instance==null){
			instance=new SdUserManager();	
		}
		return instance;
	}
	
	/**
	 * 用户登录;
	 */
	public void login(String userName,String password,SaveListener saveListener){
		
		SdService sdService=SdChat.getInstance(mContext).getService();
		
		if(sdService!=null){
			sdService.login(userName, password,saveListener);
			
		}else {
			saveListener.onFailure(SdConstants.MSG_CORE_SERVICE_NULL);
		}
	}
	
	/**
	 * 用户注册;
	 * @param userName
	 * @param password
	 * @param saveListener
	 */
	public void register( String account, String password, SaveListener saveListener){
		
		SdService sdService=SdChat.getInstance(mContext).getService();
		
		if(sdService!=null){
			sdService.register(account, password, saveListener);
			
		}else {
			saveListener.onFailure(SdConstants.MSG_CORE_SERVICE_NULL);
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
