package cn.shoudle.smack;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.provider.PrivacyProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.GroupChatInvitation;
import org.jivesoftware.smackx.PrivateDataManager;
import org.jivesoftware.smackx.ReportedData;
import org.jivesoftware.smackx.ReportedData.Row;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.bytestreams.socks5.provider.BytestreamsProvider;
import org.jivesoftware.smackx.packet.ChatStateExtension;
import org.jivesoftware.smackx.packet.LastActivity;
import org.jivesoftware.smackx.packet.OfflineMessageInfo;
import org.jivesoftware.smackx.packet.OfflineMessageRequest;
import org.jivesoftware.smackx.packet.SharedGroupsInfo;
import org.jivesoftware.smackx.packet.VCard;
import org.jivesoftware.smackx.provider.AdHocCommandDataProvider;
import org.jivesoftware.smackx.provider.DataFormProvider;
import org.jivesoftware.smackx.provider.DelayInformationProvider;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;
import org.jivesoftware.smackx.provider.DiscoverItemsProvider;
import org.jivesoftware.smackx.provider.MUCAdminProvider;
import org.jivesoftware.smackx.provider.MUCOwnerProvider;
import org.jivesoftware.smackx.provider.MUCUserProvider;
import org.jivesoftware.smackx.provider.MessageEventProvider;
import org.jivesoftware.smackx.provider.MultipleAddressesProvider;
import org.jivesoftware.smackx.provider.RosterExchangeProvider;
import org.jivesoftware.smackx.provider.StreamInitiationProvider;
import org.jivesoftware.smackx.provider.VCardProvider;
import org.jivesoftware.smackx.provider.XHTMLExtensionProvider;
import org.jivesoftware.smackx.search.UserSearch;















import org.jivesoftware.smackx.search.UserSearchManager;

import android.graphics.drawable.Drawable;
import cn.shoudle.service.SdService;
import cn.shoudle.util.FormatToolsUtil;
import cn.shoudle.util.SdLog;
import cn.shoudle.v1.SdConfig;
import cn.shoudle.v1.SdMessage;

/**
 * 服务器连接类;
 * @author Render;
 *
 */
public class XmppConnectionManager {

	private XMPPConnection mXmppConnection=null;
	private static XmppConnectionManager mSdConnectionManager=new XmppConnectionManager();
	private SdService mSdService;
	
	/**
	 * 单例模式;
	 * @return
	 */
	synchronized public static XmppConnectionManager getInstance(){
		return mSdConnectionManager;
	}
	
	/**
	 * 关联服务对象;
	 * @param sdService
	 */
	public void setSdService(SdService sdService){
		this.mSdService=sdService;
	}
	
	public XMPPConnection getConnection(){
		if(mXmppConnection==null){
			openConnection();
		}
		
		return mXmppConnection;
	}
	
	/**
	 * 连接状态监听器;
	 */
	ConnectionListener mConnectionListener=new ConnectionListener() {
		
		@Override
		public void reconnectionSuccessful() {
			
		}
		
		@Override
		public void reconnectionFailed(Exception arg0) {
			
		}
		
		@Override
		public void reconnectingIn(int arg0) {

		}
		
		@Override
		public void connectionClosedOnError(Exception e) {
			
			mSdService.postConnectionFailed(e.getMessage());
		}
		
		@Override
		public void connectionClosed() {
			
		}
	};
	
	/**
	 * 打开连接;
	 * @return
	 */
	private boolean openConnection(){
		try {
			if(null==mXmppConnection){
				
				XMPPConnection.DEBUG_ENABLED=true;
				ConnectionConfiguration config=new ConnectionConfiguration(SdConfig.SD_SERVER,
						SdConfig.DEFAULT_PORT,SdConfig.SERVER_NAME);
				config.setReconnectionAllowed(true);
				config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
				config.setSendPresence(true);
				config.setSASLAuthenticationEnabled(false);  //是否启用安全验证;
				config.setTruststorePath("/system/etc/security/cacerts.bks");  
                config.setTruststorePassword("changeit");  
                config.setTruststoreType("bks");  
                mXmppConnection = new XMPPConnection(config);  
                mXmppConnection.connect();// 连接到服务器  
                
                // 配置各种Provider，如果不配置，则会无法解析数据 ; 
                configureConnection(ProviderManager.getInstance());
                
                return true;
			}
		} catch (XMPPException e) {
			e.printStackTrace();
			mXmppConnection=null;
		}
		
		return false;
	}
	
	
	/**
	 * 关闭连接;
	 */
	private void closeConnection(){
		
		if(mXmppConnection==null)
			return;
		
		if (mXmppConnection.isConnected()) {
			new Thread() {
				public void run() {
					SdLog.i("shutDown thread started");
					mXmppConnection.disconnect();
					SdLog.i("shutDown thread finished");
				}
			}.start();
		}
		SdLog.i("关闭连接");
	}
	
	/**
	 * 登录;
	 * @param account
	 * @param password
	 * @return
	 */
	public boolean login(String account,String password){
		try{
			if(getConnection()==null){
				return false;
			}
			if(mXmppConnection.isConnected()){
				mXmppConnection.disconnect();
				mXmppConnection=null;
			}
			getConnection().login(account, password);
			
			//更改在线状态;
			Presence presence = new Presence(Presence.Type.available);
			getConnection().sendPacket(presence);
			
			//添加连接状态监听;
			mXmppConnection.addConnectionListener(mConnectionListener);

			return true;
		}
		catch (XMPPException e) {
			e.printStackTrace();
			SdLog.i(e.getLocalizedMessage());
		}
		return false;
	}
	
	/**
	 * 用户注册;
	 * @param account
	 * @param password
	 * @return
	 */
	public String register(String account, String password){
		
		if (getConnection() == null)  
		{
            return SdMessage.MSG_SERVICE_ERROR;  
		}
		
        Registration reg = new Registration();  
        reg.setType(IQ.Type.SET);  
        reg.setTo(getConnection().getServiceName());  
        
        // 注意这里createAccount注册时，参数是UserName，不是jid，是"@"前面的部分。  
        reg.setUsername(account);  
        reg.setPassword(password);  
        
        // 这边addAttribute不能为空，否则出错。
        reg.addAttribute("deviceType", "android"); 
        PacketFilter filter = new AndFilter(new PacketIDFilter(  
                reg.getPacketID()), new PacketTypeFilter(IQ.class));  
              
        PacketCollector collector = getConnection().createPacketCollector(filter);  
        getConnection().sendPacket(reg);  
        IQ result = (IQ) collector.nextResult(SmackConfiguration.getPacketReplyTimeout());  
        // Stop queuing results停止请求results（是否成功的结果）  
        collector.cancel();  
        if (result == null) {  
            SdLog.e("regist", "No response from server.");  
            return SdMessage.MSG_SERVICE_ERROR;  
        } else if (result.getType() == IQ.Type.RESULT) {  
        	SdLog.v("regist", "regist success.");  
            return SdMessage.MSG_RGISTER_SUCCESS;  
        } else {   
            if (result.getError().toString().equalsIgnoreCase("conflict(409)")) {  
            	SdLog.e("regist", "IQ.Type.ERROR: " + result.getError().toString());  
                return SdMessage.MSG_ACCOUNT_EXIST;  
            } else {  
            	SdLog.e("regist", "IQ.Type.ERROR: "  + result.getError().toString());  
                return SdMessage.MSG_RGISTER_FAILED;  
            }  
        }  
    }  

	/**
	 * 退出;
	 */
	public void logout(){
		closeConnection();
	}
	
    /** 
     * 获取所有组 
     *  
     * @return 所有组集合 
     */  
    public List<RosterGroup> getGroups() {  
        if (getConnection() == null)  
            return null;  
        List<RosterGroup> grouplist = new ArrayList<RosterGroup>();  
        Collection<RosterGroup> rosterGroup = getConnection().getRoster()  
                .getGroups();  
        Iterator<RosterGroup> i = rosterGroup.iterator();  
        while (i.hasNext()) {  
            grouplist.add(i.next());  
        }  
        return grouplist;  
    }  
  
    /** 
     * 获取某个组里面的所有好友  
     * @param roster 
     * @param groupName 组名 
     * @return 
     */  
    public List<RosterEntry> getEntriesByGroup(String groupName) {  
        if (getConnection() == null)  
            return null;  
        
        List<RosterEntry> Entrieslist = new ArrayList<RosterEntry>();  
        RosterGroup rosterGroup = getConnection().getRoster().getGroup(  
                groupName);  
        Collection<RosterEntry> rosterEntry = rosterGroup.getEntries();  
        Iterator<RosterEntry> i = rosterEntry.iterator();  
        while (i.hasNext()) {  
            Entrieslist.add(i.next());  
        }  
        return Entrieslist;  
    }  
  
    /** 
     * 获取所有好友信息 
     *  
     * @return 
     */  
    public List<RosterEntry> getAllEntries() {  
        if (getConnection() == null)  
            return null;  
        List<RosterEntry> Entrieslist = new ArrayList<RosterEntry>();  
        Collection<RosterEntry> rosterEntry = getConnection().getRoster()  
                .getEntries();  
        Iterator<RosterEntry> i = rosterEntry.iterator();  
        while (i.hasNext()) {  
            Entrieslist.add(i.next());  
        }  
        return Entrieslist;  
    }  
  
    /** 
     * 获取用户VCard信息 
     *  
     * @param connection 
     * @param user 
     * @return 
     * @throws XMPPException 
     */  
    public VCard getUserVCard(String user) {  
        if (getConnection() == null)  
            return null;  
        VCard vcard = new VCard();  
        try {  
            vcard.load(getConnection(), user);  
        } catch (XMPPException e) {  
            e.printStackTrace();  
        }  
        return vcard;  
    }  
  
    /** 
     * 获取用户头像信息 
     *  
     * @param connection 
     * @param user 
     * @return 
     */  
    public Drawable getUserImage(String user) {  
        if (getConnection() == null)  
            return null;  
        ByteArrayInputStream bais = null;  
        try {  
            VCard vcard = new VCard();  
            // 加入这句代码，解决No VCard for  
            ProviderManager.getInstance().addIQProvider("vCard", "vcard-temp",  
                    new org.jivesoftware.smackx.provider.VCardProvider());  
            if (user == "" || user == null || user.trim().length() <= 0) {  
                return null;  
            }  
            vcard.load(getConnection(), user + "@"  
                    + getConnection().getServiceName());  
  
            if (vcard == null || vcard.getAvatar() == null)  
                return null;  
            bais = new ByteArrayInputStream(vcard.getAvatar());  
        } catch (Exception e) {  
            e.printStackTrace();  
            return null;  
        }  
        return FormatToolsUtil.getInstance().InputStream2Drawable(bais);  
    }  
  
    /** 
     * 添加一个分组 
     * @param groupName 
     * @return 
     */  
    public boolean addGroup(String groupName) {  
        if (getConnection() == null)  
            return false;  
        try {  
            getConnection().getRoster().createGroup(groupName);  
            SdLog.v("addGroup", groupName + "創建成功");  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
    }  
	
    /** 
     * 添加好友 无分组 
     *  
     * @param userName 
     * @param name 
     * @return 
     */  
    public boolean addUser(String userName, String name) {  
        if (getConnection() == null)  
            return false;  
        try {  
            getConnection().getRoster().createEntry(userName, name, null);  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
    }  
  
    /** 
     * 添加好友 有分组 
     *  
     * @param userName 
     * @param name 
     * @param groupName 
     * @return 
     */  
    public boolean addUser(String userName, String name, String groupName) {  
        if (getConnection() == null)  
            return false;  
        try {  
            Presence subscription = new Presence(Presence.Type.subscribed);  
            subscription.setTo(userName);  
            userName += "@" + getConnection().getServiceName();  
            getConnection().sendPacket(subscription);  
            getConnection().getRoster().createEntry(userName, name,  
                    new String[] { groupName });  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
    }  
  
    /** 
     * 删除好友 
     *  
     * @param userName 
     * @return 
     */  
    public boolean removeUser(String userName) {  
        if (getConnection() == null)  
            return false;  
        try {  
            RosterEntry entry = null;  
            if (userName.contains("@"))  
                entry = getConnection().getRoster().getEntry(userName);  
            else  
                entry = getConnection().getRoster().getEntry(  
                        userName + "@" + getConnection().getServiceName());  
            if (entry == null)  
                entry = getConnection().getRoster().getEntry(userName);  
            getConnection().getRoster().removeEntry(entry);  
  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
    }  
  
    /** 
     * 查询用户 
     *  
     * @param userName 
     * @return 
     * @throws XMPPException 
     */  
    public List<HashMap<String, String>> searchUsers(String userName) {  
        if (getConnection() == null)  
            return null;  
        HashMap<String, String> user = null;  
        List<HashMap<String, String>> results = new ArrayList<HashMap<String, String>>();  
        try {  
            new ServiceDiscoveryManager(getConnection());  
  
            UserSearchManager usm = new UserSearchManager(getConnection());  
  
            Form searchForm = usm.getSearchForm(getConnection()  
                    .getServiceName());  
            Form answerForm = searchForm.createAnswerForm();  
            answerForm.setAnswer("userAccount", true);  
            answerForm.setAnswer("userPhote", userName);  
            ReportedData data = usm.getSearchResults(answerForm, "search"  
                    + getConnection().getServiceName());  
  
            Iterator<Row> it = data.getRows();  
            Row row = null;  
            while (it.hasNext()) {  
                user = new HashMap<String, String>();  
                row = it.next();  
                user.put("userAccount", row.getValues("userAccount").next()  
                        .toString());  
                user.put("userPhote", row.getValues("userPhote").next()  
                        .toString());  
                results.add(user);  
                // 若存在，则有返回,UserName一定非空，其他两个若是有设，一定非空  
            }  
        } catch (XMPPException e) {  
            e.printStackTrace();  
        }  
        return results;  
    }  
  
    /** 
     * 修改心情 
     *  
     * @param connection 
     * @param status 
     */  
    public void changeStateMessage(String status) {  
        if (getConnection() == null)  
            return;  
        Presence presence = new Presence(Presence.Type.available);  
        presence.setStatus(status);  
        getConnection().sendPacket(presence);  
    }  
  
    /** 
     * 修改用户头像 
     *  
     * @param file 
     */  
    public boolean changeImage(File file) {  
        if (getConnection() == null)  
            return false;  
        try {  
            VCard vcard = new VCard();  
            vcard.load(getConnection());  
  
            byte[] bytes;  
  
            bytes = getFileBytes(file);  
            
            String encodedImage = StringUtils.encodeBase64(bytes);  
            vcard.setAvatar(bytes, encodedImage);  
            vcard.setEncodedImage(encodedImage);  
            vcard.setField("PHOTO", "<TYPE>image/jpg</TYPE><BINVAL>"  
                    + encodedImage + "</BINVAL>", true);  
  
            ByteArrayInputStream bais = new ByteArrayInputStream(  
                    vcard.getAvatar());  
            FormatToolsUtil.getInstance().InputStream2Bitmap(bais);  
  
            vcard.save(getConnection());  
            return true;  
        } catch (Exception e) {  
            e.printStackTrace();  
            return false;  
        }  
    }  
  
    /** 
     * 文件转字节 
     *  
     * @param file 
     * @return 
     * @throws IOException 
     */  
    private byte[] getFileBytes(File file) throws IOException {  
        BufferedInputStream bis = null;  
        try {  
            bis = new BufferedInputStream(new FileInputStream(file));  
            int bytes = (int) file.length();  
            byte[] buffer = new byte[bytes];  
            int readBytes = bis.read(buffer);  
            if (readBytes != buffer.length) {  
                throw new IOException("Entire file not read");  
            }  
            return buffer;  
        } finally {  
            if (bis != null) {  
                bis.close();  
            }  
        }  
    }  
  
    /** 
     * 删除当前用户 
     *  
     * @return 
     */  
    public boolean deleteAccount() {  
        if (getConnection() == null)  
            return false;  
        try {  
            getConnection().getAccountManager().deleteAccount();  
            return true;  
        } catch (XMPPException e) {  
            return false;  
        }  
    }  
  
    /** 
     * 修改密码 
     *  
     * @return 
     */  
    public boolean changePassword(String pwd) {  
        if (getConnection() == null)  
            return false;  
        try {  
            getConnection().getAccountManager().changePassword(pwd);  
            return true;  
        } catch (XMPPException e) {  
            return false;  
        }  
    }  
    
	/**
	 * 判断是否已经认证;
	 * @return
	 */
	public boolean isAuthenticated() {
		if (mXmppConnection != null) {
			return (mXmppConnection.isConnected() && mXmppConnection
					.isAuthenticated());
		}
		return false;
	}
	
    /** 
     * 加入providers的函数 ASmack在/META-INF缺少一个smack.providers 文件 
     * @param pm 
     */  
    public void configureConnection(ProviderManager pm) {  
  
        // Private Data Storage  
        pm.addIQProvider("query", "jabber:iq:private",  
                new PrivateDataManager.PrivateDataIQProvider());  
  
        // Time  
        try {  
            pm.addIQProvider("query", "jabber:iq:time",  
                    Class.forName("org.jivesoftware.smackx.packet.Time"));  
        } catch (ClassNotFoundException e) {  
            SdLog.w("TestClient",  
                    "Can't load class for org.jivesoftware.smackx.packet.Time");  
        }  
  
        // Roster Exchange  
        pm.addExtensionProvider("x", "jabber:x:roster",  
                new RosterExchangeProvider());  
  
        // Message Events  
        pm.addExtensionProvider("x", "jabber:x:event",  
                new MessageEventProvider());  
  
        // Chat State  
        pm.addExtensionProvider("active",  
                "http://jabber.org/protocol/chatstates",  
                new ChatStateExtension.Provider());  
        pm.addExtensionProvider("composing",  
                "http://jabber.org/protocol/chatstates",  
                new ChatStateExtension.Provider());  
        pm.addExtensionProvider("paused",  
                "http://jabber.org/protocol/chatstates",  
                new ChatStateExtension.Provider());  
        pm.addExtensionProvider("inactive",  
                "http://jabber.org/protocol/chatstates",  
                new ChatStateExtension.Provider());  
        pm.addExtensionProvider("gone",  
                "http://jabber.org/protocol/chatstates",  
                new ChatStateExtension.Provider());  
  
        // XHTML  
        pm.addExtensionProvider("html", "http://jabber.org/protocol/xhtml-im",  
                new XHTMLExtensionProvider());  
  
        // Group Chat Invitations  
        pm.addExtensionProvider("x", "jabber:x:conference",  
                new GroupChatInvitation.Provider());  
  
        // Service Discovery # Items  
        pm.addIQProvider("query", "http://jabber.org/protocol/disco#items",  
                new DiscoverItemsProvider());  
  
        // Service Discovery # Info  
        pm.addIQProvider("query", "http://jabber.org/protocol/disco#info",  
                new DiscoverInfoProvider());  
  
        // Data Forms  
        pm.addExtensionProvider("x", "jabber:x:data", new DataFormProvider());  
  
        // MUC User  
        pm.addExtensionProvider("x", "http://jabber.org/protocol/muc#user",  
                new MUCUserProvider());  
  
        // MUC Admin  
        pm.addIQProvider("query", "http://jabber.org/protocol/muc#admin",  
                new MUCAdminProvider());  
  
        // MUC Owner  
        pm.addIQProvider("query", "http://jabber.org/protocol/muc#owner",  
                new MUCOwnerProvider());  
  
        // Delayed Delivery  
        pm.addExtensionProvider("x", "jabber:x:delay",  
                new DelayInformationProvider());  
  
        // Version  
        try {  
            pm.addIQProvider("query", "jabber:iq:version",  
                    Class.forName("org.jivesoftware.smackx.packet.Version"));  
        } catch (ClassNotFoundException e) {  
            // Not sure what's happening here.  
        }  
  
        // VCard  
        pm.addIQProvider("vCard", "vcard-temp", new VCardProvider());  
  
        // Offline Message Requests  
        pm.addIQProvider("offline", "http://jabber.org/protocol/offline",  
                new OfflineMessageRequest.Provider());  
  
        // Offline Message Indicator  
        pm.addExtensionProvider("offline",  
                "http://jabber.org/protocol/offline",  
                new OfflineMessageInfo.Provider());  
  
        // Last Activity  
        pm.addIQProvider("query", "jabber:iq:last", new LastActivity.Provider());  
  
        // User Search  
        pm.addIQProvider("query", "jabber:iq:search", new UserSearch.Provider());  
  
        // SharedGroupsInfo  
        pm.addIQProvider("sharedgroup",  
                "http://www.jivesoftware.org/protocol/sharedgroup",  
                new SharedGroupsInfo.Provider());  
  
        // JEP-33: Extended Stanza Addressing  
        pm.addExtensionProvider("addresses",  
                "http://jabber.org/protocol/address",  
                new MultipleAddressesProvider());  
  
        // FileTransfer  
        pm.addIQProvider("si", "http://jabber.org/protocol/si",  
                new StreamInitiationProvider());  
  
        pm.addIQProvider("query", "http://jabber.org/protocol/bytestreams",  
                new BytestreamsProvider());  
  
        // Privacy  
        pm.addIQProvider("query", "jabber:iq:privacy", new PrivacyProvider());  
        pm.addIQProvider("command", "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider());  
        pm.addExtensionProvider("malformed-action",  
                "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider.MalformedActionError());  
        pm.addExtensionProvider("bad-locale",  
                "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider.BadLocaleError());  
        pm.addExtensionProvider("bad-payload",  
                "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider.BadPayloadError());  
        pm.addExtensionProvider("bad-sessionid",  
                "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider.BadSessionIDError());  
        pm.addExtensionProvider("session-expired",  
                "http://jabber.org/protocol/commands",  
                new AdHocCommandDataProvider.SessionExpiredError());  
    }  
}
