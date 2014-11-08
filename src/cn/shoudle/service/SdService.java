package cn.shoudle.service;

import java.util.List;

import cn.shoudle.listener.EventListener;
import cn.shoudle.listener.SaveListener;
import cn.shoudle.push.SdBroadcastReceiver;
import cn.shoudle.smack.XmppConnectionManager;
import cn.shoudle.util.NetUtil;
import cn.shoudle.util.PreferenceUtils;
import cn.shoudle.util.SdLog;
import cn.shoudle.v1.SdConstants;
import cn.shoudle.v1.SdMessage;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;

/**
 * 核心服务类,所有跟服务器的操作应该在这里进行实现;
 * @author Render;
 *
 */
public class SdService extends Service implements EventListener{

	private static final String TAG = "SdService";
	public static final String LOGOUT = "logout";// 手动退出

	private IBinder mBinder = new SdBinder();
	private Handler mMainHandler = new Handler();
	private Thread mConnectingThread;
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 10 * 60;// 最大重连时间间隔
	private static final String RECONNECT_ALARM = "cn.shoudle.RECONNECT_ALARM";
	private int mReconnectTimeout = RECONNECT_AFTER;
	private Intent mAlarmIntent = new Intent(RECONNECT_ALARM);
	private PendingIntent mPAlarmIntent;
	private BroadcastReceiver mAlarmReceiver = new ReconnectAlarmReceiver();
	
	// 自动重连 end
	private ActivityManager mActivityManager;
	private String mAccount;
	private String mPassword;
	
	@Override
	public IBinder onBind(Intent intent) {
		SdLog.i(TAG, "called onBind()");
		
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		SdLog.i(TAG, "called onUnbind()");
		
		return true;
	}

	@Override
	public void onRebind(Intent intent) {
		SdLog.i(TAG, "called onRebind()");
		
		super.onRebind(intent);
	}

	@Override
	public void onCreate() {
		SdLog.i(TAG, "called onCreate()");
		super.onCreate();
		
		//把服务对象设置到连接管理器中;
		XmppConnectionManager.getInstance().setSdService(this);
		
		//把该服务添加到广播事件中;
		SdBroadcastReceiver.mListeners.add(this);
		mPAlarmIntent = PendingIntent.getBroadcast(this, 0, mAlarmIntent,PendingIntent.FLAG_UPDATE_CURRENT);
		registerReceiver(mAlarmReceiver, new IntentFilter(RECONNECT_ALARM));
	}

    /**
     * 监听手机开机时，是否直接连接;
     */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		SdLog.i(TAG, "called onStartCommand()");
	
		if (intent != null
				&& intent.getAction() != null
				&& TextUtils.equals(intent.getAction(),
						SdBroadcastReceiver.BOOT_COMPLETED_ACTION)) {
			String account = PreferenceUtils.getPrefString(SdService.this,
					SdConstants.CONS_ACCOUNT, "");
			String password = PreferenceUtils.getPrefString(SdService.this,
					SdConstants.CONS_PASSWORD, "");
			if (!TextUtils.isEmpty(account) && !TextUtils.isEmpty(password))
				login(account, password,null);
		}
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		SdLog.i(TAG, "called onDestroy()");
		super.onDestroy();
		SdBroadcastReceiver.mListeners.remove(this);
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);// 取消重连闹钟
		unregisterReceiver(mAlarmReceiver);// 注销广播监听
		logout();
	}

/*	// 发送消息
	public void sendMessage(String user, String message) {
		if (mSmackable != null)
			mSmackable.sendMessage(user, message);
		//else
			//SmackImpl.sendOfflineMessage(getContentResolver(), user, message);
	}
*/
	/**
	 * 是否连接上服务器
	 * @return
	 */
	public boolean isAuthenticated() {
		
		return XmppConnectionManager.getInstance().isAuthenticated();
	}

	/**
	 * 非UI线程连接失败反馈
	 * @param reason
	 */
	public void postConnectionFailed(final String reason) {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionFailed(reason);
			}
		});
	}
	
	/**
	 * 连接成功反馈;
	 */
	private void postConnectionScuessed() {
		mMainHandler.post(new Runnable() {
			public void run() {
				connectionScuessed();
			}
		});
	}
	
	/**
	 * UI线程反馈连接失败
	 * 
	 * @param reason
	 */
	private void connectionFailed(String reason) {
		
		SdLog.i(TAG, "connectionFailed: " + reason);
		
		if (TextUtils.equals(reason, LOGOUT)) {// 如果是手动退出
			((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
			return;
		}
		
		//判断是否是第一次登录;
		boolean bFirstLogin = PreferenceUtils.getPrefBoolean(SdService.this,
				SdConstants.CONS_FIRST_LOGIN,true);
		
		//如果是第一次登录，即使登录失败也不进行重练;
		if(bFirstLogin)
			return;
		
		// 无网络连接时,直接返回
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
			return;
		}
	
		String account = PreferenceUtils.getPrefString(SdService.this,SdConstants.CONS_ACCOUNT, "");
		String password = PreferenceUtils.getPrefString(SdService.this,SdConstants.CONS_PASSWORD, "");
		
		// 无保存的帐号密码时，也直接返回
		if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
			SdLog.d(TAG,"account = null || password = null");
			return;
		}
		
		// 如果不是手动退出并且需要重新连接，则开启重连闹钟
		if (PreferenceUtils.getPrefBoolean(this,
				SdConstants.CONS_AUTO_RECONNECT, true)) {
			SdLog.d(TAG,"connectionFailed(): registering reconnect in "+ mReconnectTimeout + "s");
			((AlarmManager) getSystemService(Context.ALARM_SERVICE)).set(
					AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+ mReconnectTimeout * 1000, mPAlarmIntent);
			mReconnectTimeout = mReconnectTimeout * 2;
			if (mReconnectTimeout > RECONNECT_MAXIMUM)
				mReconnectTimeout = RECONNECT_MAXIMUM;
			} else {
				((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
			}
		}

	/**
	 * 连接成功;
	 */
	private void connectionScuessed() {

		mReconnectTimeout = RECONNECT_AFTER;// 重置重连的时间
		PreferenceUtils.setPrefString(this, SdConstants.CONS_ACCOUNT,mAccount);//登录后账号自动保存;
		PreferenceUtils.setPrefString(this, SdConstants.CONS_PASSWORD,mPassword);
		PreferenceUtils.setPrefBoolean(this, SdConstants.CONS_FIRST_LOGIN, false);
	}

	/** 
	 * 收到新消息;
	 * @param from
	 * @param message
	 */
	public void newMessage(final String from, final String message) {
		mMainHandler.post(new Runnable() {
			public void run() {
				
			}
		});
	}

	public boolean isAppOnForeground() {
		List<RunningTaskInfo> taskInfos = mActivityManager.getRunningTasks(1);
		if (taskInfos.size() > 0
				&& TextUtils.equals(getPackageName(),
						taskInfos.get(0).topActivity.getPackageName())) {
			return true;
		}
		return false;
	}

	/**
	 * 该闹钟事件广播接收器，实现自动连接;
	 * @author Render
	 */
	private class ReconnectAlarmReceiver extends BroadcastReceiver {
		public void onReceive(Context ctx, Intent i) {
			SdLog.d(TAG,"Alarm received.");
			if (!PreferenceUtils.getPrefBoolean(SdService.this,
					SdConstants.CONS_AUTO_RECONNECT, true)) {
				return;
			}
			String account = PreferenceUtils.getPrefString(SdService.this,
					SdConstants.CONS_ACCOUNT, "");
			String password = PreferenceUtils.getPrefString(SdService.this,
					SdConstants.CONS_PASSWORD, "");
			if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
				SdLog.d(TAG,"account = null || password = null");
				return;
			}
			login(account, password,null);
		}
	}

	/**
	 * 监听网络改变的状态;
	 */
	@Override
	public void onNetChange() {
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {// 如果是网络断开，不作处理
			connectionFailed(SdMessage.MSG_NETWORK_ERROR);
			return;
		}
		if (isAuthenticated())// 如果已经连接上，直接返回
			return;
		String account = PreferenceUtils.getPrefString(SdService.this,
				SdConstants.CONS_ACCOUNT, "");
		String password = PreferenceUtils.getPrefString(SdService.this,
				SdConstants.CONS_PASSWORD, "");
		if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password))// 如果没有帐号，也直接返回
			return;
		if (!PreferenceUtils.getPrefBoolean(this,SdConstants.CONS_AUTO_RECONNECT, true))// 不需要重连
			return;
		
		login(account, password,null);// 重连;
	}
		
	public class SdBinder extends Binder {
		public SdService getService() {
			return SdService.this;
		}
	}
		
	/**
	 * 登录;
	 * @param account
	 * @param password
	 * @param saveListener
	 */
	public void login(final String account, final String password,final SaveListener saveListener) {
		
		this.mAccount=account;
		this.mPassword=password;
		
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			connectionFailed(SdMessage.MSG_NETWORK_ERROR);
			saveListener.onFailure(SdMessage.MSG_NETWORK_ERROR);
			return;
		}
		
		mConnectingThread = new Thread() {
			@Override
			public void run() {
				try {
					if (XmppConnectionManager.getInstance().login(account, password)) {
						// 登陆成功
						postConnectionScuessed();
						
						if(saveListener!=null){
							saveListener.onSuccess();
						}
						
					} else {
						// 登陆失败
						postConnectionFailed(SdMessage.MSG_LOGIN_FAILED);
						
						if(saveListener!=null){
							saveListener.onFailure(SdMessage.MSG_LOGIN_FAILED);
						}
					}
				} catch (Exception e) {
					String message = e.getLocalizedMessage();
					// 登陆失败
					if (e.getCause() != null)
						message += "\n" + e.getCause().getLocalizedMessage();
					postConnectionFailed(message);
					
					if(saveListener!=null){
						saveListener.onFailure(SdMessage.MSG_LOGIN_FAILED);
					}
					
					SdLog.i("XMPPException in doConnect():");
					e.printStackTrace();
				} finally {
					if (mConnectingThread != null)
						synchronized (mConnectingThread) {
							mConnectingThread = null;
						}
				}
			}

		};
		mConnectingThread.start();
	}
	
	/**
	 * 注册用户;
	 * @param account
	 * @param password
	 * @param saveListener
	 */
	public void register(final String account, final String password,final SaveListener saveListener){
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			saveListener.onFailure(SdMessage.MSG_NETWORK_ERROR);
			return ;
		}
		
		mConnectingThread=new Thread(){
			@Override
			public void run() {
				
				try {
					String strResult=XmppConnectionManager.getInstance().register(account, password);
					if(strResult.equals(SdMessage.MSG_RGISTER_SUCCESS)){
						
						saveListener.onSuccess();
						
					}else {
						saveListener.onFailure(strResult);
					}				
				} catch (Exception e) {
					String message = e.getLocalizedMessage();
					
					if (e.getCause() != null){
						message += "\n" + e.getCause().getLocalizedMessage();
					}
					saveListener.onFailure(SdMessage.MSG_RGISTER_FAILED);
					SdLog.i("YaximXMPPException in doConnect():");
					e.printStackTrace();
				} finally {
					if (mConnectingThread != null)
						synchronized (mConnectingThread) {
							mConnectingThread = null;
						}
				}
			}
		};
		mConnectingThread.start();
	}
	
	/**
	 * 退出;
	 * @return
	 */
	public boolean logout() {
		
		if (mConnectingThread != null) {
			synchronized (mConnectingThread) {
				try {
					mConnectingThread.interrupt();
					mConnectingThread.join(50);
				} catch (InterruptedException e) {
					SdLog.i("doDisconnect: failed catching connecting thread");
				} finally {
					mConnectingThread = null;
				}
			}
		}
		
		XmppConnectionManager.getInstance().logout();
		connectionFailed(LOGOUT);// 手动退出;
		
		//设置为第一次登录;
	    PreferenceUtils.setPrefBoolean(SdService.this,SdConstants.CONS_FIRST_LOGIN,true);
	    PreferenceUtils.setPrefString(SdService.this,SdConstants.CONS_ACCOUNT,"");
	    PreferenceUtils.setPrefString(SdService.this,SdConstants.CONS_PASSWORD,"");
	    
		return true;
	}

}
