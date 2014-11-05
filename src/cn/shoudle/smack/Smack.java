package cn.shoudle.smack;

public interface Smack {
	
	/**
	 * 登录;
	 * @param account
	 * @param password
	 * @return
	 * @throws SdException
	 */
	public boolean login(String account, String password) throws SdException;
	
	/**
	 * 注册;
	 * @param account
	 * @param password
	 * @return
	 * @throws SdException
	 */
    public boolean register(String account,String password) throws SdException;
	
    /**
     * 退出;
     * @return
     */
	public boolean logout();

	public boolean isAuthenticated();

	public void addRosterItem(String user, String alias, String group)
			throws SdException;

	public void removeRosterItem(String user) throws SdException;

	public void renameRosterItem(String user, String newName)
			throws SdException;

	public void moveRosterItemToGroup(String user, String group)
			throws SdException;

	public void renameRosterGroup(String group, String newGroup);

	public void requestAuthorizationForRosterItem(String user);

	public void addRosterGroup(String group);

	public void setStatusFromConfig();

	public void sendMessage(String user, String message);

	public void sendServerPing();

	public String getNameForJID(String jid);
}
