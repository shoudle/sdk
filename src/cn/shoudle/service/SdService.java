package cn.shoudle.service;

import java.util.List;

import cn.shoudle.listener.EventListener;
import cn.shoudle.listener.SaveListener;
import cn.shoudle.push.SdBroadcastReceiver;
import cn.shoudle.smack.SdException;
import cn.shoudle.smack.SmackImpl;
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

public class SdService extends Service implements EventListener{

	private static final String TAG = "SdService";
	
	public static final int CONNECTED = 0;
	public static final int DISCONNECTED = -1;
	public static final int CONNECTING = 1;
	public static final String PONG_TIMEOUT = "pong timeout";// 连接超时
	public static final String LOGOUT = "logout";// 手动退出
	public static final String DISCONNECTED_WITHOUT_WARNING = "disconnected without warning";// 没有警告的断开连接

	private IBinder mBinder = new SdBinder();
	private SmackImpl mSmackable;
	private Thread mConnectingThread;
	private Handler mMainHandler = new Handler();
	
	private static final int RECONNECT_AFTER = 5;
	private static final int RECONNECT_MAXIMUM = 10 * 60;// 最大重连时间间隔
	private static final String RECONNECT_ALARM = "cn.shoudle.RECONNECT_ALARM";
	private int mConnectedState = DISCONNECTED; // 是否已经连接
	private int mReconnectTimeout = RECONNECT_AFTER;
	private Intent mAlarmIntent = new Intent(RECONNECT_ALARM);
	private PendingIntent mPAlarmIntent;
	private BroadcastReceiver mAlarmReceiver = new ReconnectAlarmReceiver();
	
	// 自动重连 end
	private ActivityManager mActivityManager;
	private String mPackageName;
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
		
		//把该服务添加到广播事件中;
		SdBroadcastReceiver.mListeners.add(this);
		mPackageName = getPackageName();
		mPAlarmIntent = PendingIntent.getBroadcast(this, 0, mAlarmIntent,PendingIntent.FLAG_UPDATE_CURRENT);
		registerReceiver(mAlarmReceiver, new IntentFilter(RECONNECT_ALARM));
	}

	@Override
	public void onDestroy() {
		SdLog.i(TAG, "called onDestroy()");
		super.onDestroy();
		
		((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);// 取消重连闹钟
		unregisterReceiver(mAlarmReceiver);// 注销广播监听
		logout();
	}

	/**
	 * 登录;
	 * @param account
	 * @param password
	 * @param saveListener
	 */
	public void Login(final String account, final String password,final SaveListener saveListener) {
		
		this.mAccount=account;
		this.mPassword=password;
		
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			connectionFailed(SdMessage.MSG_NETWORK_ERROR);
			saveListener.onFailure(SdMessage.MSG_NETWORK_ERROR);
			return;
		}
		if (mConnectingThread != null) {
			SdLog.i(TAG,"a connection is still goign on!");
			return;
		}
		mConnectingThread = new Thread() {
			@Override
			public void run() {
				try {
					postConnecting();
					mSmackable = new SmackImpl(SdService.this);
					if (mSmackable.login(account, password)) {
						// 登陆成功
						postConnectionScuessed();
						saveListener.onSuccess();	
					} else {
						// 登陆失败
						postConnectionFailed(SdMessage.MSG_LOGIN_FAILED);
						saveListener.onFailure(SdMessage.MSG_LOGIN_FAILED);
					}
				} catch (SdException e) {
					String message = e.getLocalizedMessage();
					// 登陆失败
					if (e.getCause() != null){
						message += "\n" + e.getCause().getLocalizedMessage();
					}
					postConnectionFailed(message);
					saveListener.onFailure(message);
					SdLog.i(TAG, "YaximXMPPException in doConnect():");
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
	 * 注册新用户;
	 * @param userName
	 * @param password
	 */
	public void register(final String account,final String password,final SaveListener saveListener){
		
		if (NetUtil.getNetworkState(this) == NetUtil.NETWORN_NONE) {
			return;
		}
		if (mConnectingThread != null) {
			SdLog.i(TAG,"a connection is still goign on!");
			return;
		}
		mConnectingThread = new Thread() {
			@Override
			public void run() {
				try {
					postConnecting();
					mSmackable = new SmackImpl(SdService.this);
					if (mSmackable.register(account, password)) {
						saveListener.onSuccess();
					} else {
						saveListener.onFailure("");
					}
				} catch (SdException e) {
					String message = e.getLocalizedMessage();
				
					if (e.getCause() != null){
						message += "\n" + e.getCause().getLocalizedMessage();
					}
					saveListener.onFailure(message);
					SdLog.i(TAG, "YaximXMPPException in doConnect():");
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
		boolean isLogout = false;
		if (mConnectingThread != null) {
			synchronized (mConnectingThread) {
				try {
					mConnectingThread.interrupt();
					mConnectingThread.join(50);
				} catch (InterruptedException e) {
					SdLog.e(TAG,"doDisconnect: failed catching connecting thread");
				} finally {
					mConnectingThread = null;
				}
			}
		}
		if (mSmackable != null) {
			isLogout = mSmackable.logout();
			mSmackable = null;
		}
		connectionFailed(LOGOUT);// 手动退出;
		
		//设置为第一次登录;
	    PreferenceUtils.setPrefBoolean(SdService.this,SdConstants.CONS_FIRST_LOGIN,true);
		return isLogout;
	}

	// 发送消息
	public void sendMessage(String user, String message) {
		if (mSmackable != null)
			mSmackable.sendMessage(user, message);
		//else
			//SmackImpl.sendOfflineMessage(getContentResolver(), user, message);
	}

	// 是否连接上服务器
	public boolean isAuthenticated() {
		if (mSmackable != null) {
			return mSmackable.isAuthenticated();
		}

		return false;
	}
	
	// 新增联系人
	public void addRosterItem(String user, String alias, String group) {
		try {
			mSmackable.addRosterItem(user, alias, group);
		} catch (SdException e) {
			SdLog.e(TAG,"exception in addRosterItem(): " + e.getMessage());
		}
	}

	// 新增分组
	public void addRosterGroup(String group) {
		mSmackable.addRosterGroup(group);
	}

	// 删除联系人
	public void removeRosterItem(String user) {
		try {
			mSmackable.removeRosterItem(user);
		} catch (SdException e) {
			SdLog.e(TAG,"exception in removeRosterItem(): " + e.getMessage());
		}
	}

	// 将联系人移动到其他组
	public void moveRosterItemToGroup(String user, String group) {
		try {
			mSmackable.moveRosterItemToGroup(user, group);
		} catch (SdException e) {
			SdLog.e(TAG,"exception in moveRosterItemToGroup(): " + e.getMessage());
		}
	}

	// 重命名联系人
	public void renameRosterItem(String user, String newName) {
		try {
			mSmackable.renameRosterItem(user, newName);
		} catch (SdException e) {
			SdLog.e(TAG,"exception in renameRosterItem(): " + e.getMessage());
		}
	}

	// 重命名组
	public void renameRosterGroup(String group, String newGroup) {
		mSmackable.renameRosterGroup(group, newGroup);
	}

	/**
	 * 非UI线程连接失败反馈
	 * 
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
	 * UI线程反馈连接失败
	 * 
	 * @param reason
	 */
	private void connectionFailed(String reason) {
		
		SdLog.i(TAG, "connectionFailed: " + reason);
		mConnectedState = DISCONNECTED;// 更新当前连接状态
		
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
					AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
					+ mReconnectTimeout * 1000, mPAlarmIntent);
			mReconnectTimeout = mReconnectTimeout * 2;
			if (mReconnectTimeout > RECONNECT_MAXIMUM)
				mReconnectTimeout = RECONNECT_MAXIMUM;
			} else {
				((AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(mPAlarmIntent);
			}
		}

		private void postConnectionScuessed() {
			mMainHandler.post(new Runnable() {
				public void run() {
					connectionScuessed();
				}
			});
		}

		private void connectionScuessed() {
			mConnectedState = CONNECTED;// 已经连接上
			mReconnectTimeout = RECONNECT_AFTER;// 重置重连的时间
		
			PreferenceUtils.setPrefString(this, SdConstants.CONS_ACCOUNT,mAccount);//登录后账号自动保存;
			PreferenceUtils.setPrefString(this, SdConstants.CONS_PASSWORD,mPassword);
			PreferenceUtils.setPrefBoolean(this, SdConstants.CONS_FIRST_LOGIN, false);
		}

		// 连接中，通知界面线程做一些处理
		private void postConnecting() {
			mMainHandler.post(new Runnable() {
				public void run() {
					mConnectedState = CONNECTING;
				}
			});
		}

		// 收到新消息
		public void newMessage(final String from, final String message) {
			mMainHandler.post(new Runnable() {
				public void run() {
					/*if (!PreferenceUtils.getPrefBoolean(XXService.this,
							PreferenceConstants.SCLIENTNOTIFY, false))
						MediaPlayer.create(XXService.this, R.raw.office).start();
					if (!isAppOnForeground())
						notifyClient(from, mSmackable.getNameForJID(from), message,
								!mIsBoundTo.contains(from));
					// T.showLong(XXService.this, from + ": " + message);
					 */
				}
			});
		}

		// 联系人改变
		public void rosterChanged() {
			// gracefully handle^W ignore events after a disconnect
			if (mSmackable == null)
				return;
			if (mSmackable != null && !mSmackable.isAuthenticated()) {
				SdLog.i(TAG,"rosterChanged(): disconnected without warning");
				connectionFailed(DISCONNECTED_WITHOUT_WARNING);
			}
		}
		
		// 判断程序是否在后台运行的任务
		Runnable monitorStatus = new Runnable() {
			public void run() {
				try {
					SdLog.i(TAG,"monitorStatus is running... " + mPackageName);
					mMainHandler.removeCallbacks(monitorStatus);
					// 如果在后台运行并且连接上了
					if (!isAppOnForeground()) {
						SdLog.i(TAG,"app run in background...");
						// if (isAuthenticated())
						//updateServiceNotification(getString(R.string.run_bg_ticker));
						return;
					} else {
						stopForeground(true);
					}
					// mMainHandler.postDelayed(monitorStatus, 1000L);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

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
				if (mConnectedState != DISCONNECTED) {
					SdLog.d(TAG,"Reconnect attempt aborted: we are connected again!");
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
				Login(account, password,null);
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
			if (!PreferenceUtils.getPrefBoolean(this,
					SdConstants.CONS_AUTO_RECONNECT, true))// 不需要重连
				return;
			Login(account, password,null);// 重连
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
				Login(account, password,null);
		}
		mMainHandler.removeCallbacks(monitorStatus);
		mMainHandler.postDelayed(monitorStatus, 1000L);// 检查应用是否在后台运行线程
		return START_STICKY;
	}
	
	public class SdBinder extends Binder {
		public SdService getService() {
			return SdService.this;
		}
	}
}
