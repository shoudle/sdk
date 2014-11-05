package cn.shoudle.im;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import cn.shoudle.service.SdService;
import cn.shoudle.smack.SdException;
import cn.shoudle.util.SdLog;

/**
 * shoudle初始化类，主要是初始化服务类;
 * @author render;
 */
public class SdChat {

	private static final String TAG = "SdChat";
	
	/**
	 * 是否打开调试模式;
	 */
	public static boolean DEBUG_MODE=true;
	
	private static Object instance_lock;
	private SdService mSdService;
	private static Context mContext;
	private static SdChat instance;
	static{
		instance_lock=new Object();
	}
	
	public static SdChat getInstance(){

		synchronized (instance_lock) {
			if(instance==null){
				instance=new SdChat();	
			}
			return instance;
		}
	}
	
	public SdService getService(){
		
		return mSdService;
	}
	
	/**
	 * 初始化;
	 * @param context
	 */
	public void init(Context context){
		Intent mServiceIntent = new Intent(mContext, SdService.class);
		mContext.bindService(mServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		SdLog.i(TAG, "[bind service]");
	}
	
	/**
	 * 在构造函数中进行核心服务的绑定;
	 */
	private SdChat(){
		
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
}
