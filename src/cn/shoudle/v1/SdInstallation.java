package cn.shoudle.v1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.UUID;

import android.content.Context;

/**
 * 安装管理;
 * @author Render;
 */
public class SdInstallation {

	/**
	 * 设备类型;
	 */
	private String deviceType;
	
	private static String installationId=null;

	public String getDeviceType() {
		return deviceType;
	}

	public void setDeviceType(String deviceType) {
		this.deviceType = deviceType;
	}

	public String getInstallationId() {
		return installationId;
	}

	public void setInstallationId(String installationId) {
		SdInstallation.installationId = installationId;
	}
	
    public synchronized static String getInstallationId(Context context) {
    	
        if (installationId == null) { 
            File installation = new File(context.getFilesDir(), "INSTALLATION");
            try {
                if (!installation.exists()){
                    writeInstallationFile(installation);
                }
                installationId = readInstallationFile(installation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return installationId;
    }

    private static String readInstallationFile(File installation) throws IOException {
        RandomAccessFile f = new RandomAccessFile(installation, "r");
        byte[] bytes = new byte[(int) f.length()];
        f.readFully(bytes);
        f.close();
        return new String(bytes);
    }

    private static void writeInstallationFile(File installation) throws IOException {
        FileOutputStream out = new FileOutputStream(installation);
        String id = UUID.randomUUID().toString();
        out.write(id.getBytes());
        out.close();
    }
}
