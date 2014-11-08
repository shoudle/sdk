package cn.shoudle.im;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import cn.shoudle.service.SdService;
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
	private static SdService mSdService;
	private static Context mContext=null;
	private static SdChat instance;
	static{
		instance_lock=new Object();
	}
	
	public static SdChat getInstance(Context context){

		synchronized (instance_lock) {
			mContext=context;
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
	public void init(){
		
		if(mContext==null)
			return;
		
		Intent mServiceIntent = new Intent(mContext, SdService.class);
		mContext.startService(mServiceIntent);
		mContext.bindService(mServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		SdLog.i(TAG, "[bind service]");
	}
	
	/**
	 * 在构造函数中进行核心服务的绑定;
	 */
	private SdChat(){
		
	}
	
	private ServiceConnection mServiceConnection = new ServiceConnection(){

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			
			try {
				mSdService = ((SdService.SdBinder) service).getService();
			} catch (Exception e) {
				SdLog.i(e.getLocalizedMessage());
				e.printStackTrace();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mSdService = null;
		}
	};
	
	/**
	 * 主要是解除服务绑定;
	 */
	public void destory(){
		mSdService.unbindService(mServiceConnection);
	}
}
