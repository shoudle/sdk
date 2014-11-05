package cn.shoudle.im;

import cn.shoudle.listener.SaveListener;
import cn.shoudle.service.SdService;
import cn.shoudle.smack.SdException;
import cn.shoudle.util.SdLog;
import cn.shoudle.v1.SdMessage;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * 用户管理类;
 * @author Render;
 */
public class SdUserManager {

	private static final String TAG = "SdUserManager";
	private static Context mContext;
	private static SdUserManager instance;
	private static Object instance_lock;
	private SdService mSdService;
	
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
	
	ServiceConnection mServiceConnection = new ServiceConnection(){

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mSdService = ((SdService.SdBinder) service).getService();
			
			if(mSdService==null){
				try {
					throw new SdException("[bind service failed!]");
				} catch (SdException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mSdService = null;
		}
	};
	
	/**
	 * 在构造函数中进行核心服务的绑定;
	 */
	private SdUserManager(){
		Intent mServiceIntent = new Intent(mContext, SdService.class);
		mContext.bindService(mServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		SdLog.i(TAG, "[bind service]");
		
	}

	/**
	 * 用户登录;
	 */
	public void login(String userName,String password,SaveListener saveListener){
		
		if(mSdService!=null){
			mSdService.Login(userName, password,saveListener);
		}else {
			saveListener.onFailure(SdMessage.MSG_CORE_SERVICE_NULL);
		}
	}
	
	public void register(String userName,String password,SaveListener saveListener){
		if(mSdService!=null){
			mSdService.register(userName, password, saveListener);
		}else {
			saveListener.onFailure(SdMessage.MSG_CORE_SERVICE_NULL);
		}
	}
	
	/**
	 * 用户退出;
	 */
	public void logout(){
	
		if(mSdService!=null){
			mSdService.logout();
		}
	}
}
