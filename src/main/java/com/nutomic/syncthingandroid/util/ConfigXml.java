package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.service.SyncthingRunnable;

import org.mindrot.jbcrypt.BCrypt;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.DataOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import eu.chainfire.libsuperuser.Shell;
/**
 * Provides direct access to the config.xml file in the file system.
 *
 * This class should only be used if the syncthing API is not available (usually during startup).
 */
public class ConfigXml {

    public class OpenConfigException extends RuntimeException {
    }

    private static final String TAG = "ConfigXml";

    /**
     * File in the config folder that contains configuration.
     */
    public static final String CONFIG_FILE = "config.xml";

    private final Context mContext;

    private final File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) throws OpenConfigException {
        mContext = context;
        mConfigFile = getConfigFile(context);
        boolean isFirstStart = !mConfigFile.exists();
        if (isFirstStart) {
            Log.i(TAG, "App started for the first time. Generating keys and config.");
            generateKeysConfig(context);
        }

	readConfig();

        if (isFirstStart) {
            changeLocalDeviceName();
            changeDefaultFolder();
        }
    }

    private void readConfig() {
	if (!mConfigFile.canRead() && !chownConfigFile()) {
	    throw new OpenConfigException();
	}
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	    Log.d(TAG,"Trying to read '" + mConfigFile + "'");
            mConfig = db.parse(mConfigFile);
        } catch (SAXException | ParserConfigurationException | IOException e) {
	  Log.w(TAG,"Cannot read '" + mConfigFile + "'",e);
	  throw new OpenConfigException();
	}
    }

    private boolean chownConfigFile() {
	// Since either readConfig or saveChanges called this method, the permissions of config.xml got messed up. 
	// For now, the only way to do so is to run Syncthing as root, because the config file resides in the application's data directory.
	// That directory is only writeable by this application, and root of course. Syncthing will save it's config as root with 0600.
	// We can safely assume that root magic is somehow available.
	// Be paranoid :) and check if su binary is available.
	if (!Shell.SU.available()) {
	    Log.e(TAG,"Su is not available. Cannot fix permssions ");
	    return false;
	}

	try {
		ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(mContext.getPackageName(),0);
		Log.d(TAG,"Uid of '" + mContext.getPackageName() + "' is " + appInfo.uid);
		try {
		    Process chown = Runtime.getRuntime().exec("su");
		    DataOutputStream chownOut = new DataOutputStream(chown.getOutputStream());
		    String cmd = "chown " + appInfo.uid + ":" + appInfo.uid + " " + mConfigFile + "\n";
		    Log.d(TAG,"Running: '" + cmd);
		    chownOut.writeBytes(cmd);
		    // For now, we assume that a file's type should *always* be 'u:object_r:app_data_file:s0:c512,c768'.
		    // Running syncthing as root, makes it 'u:object_r:app_data_file:s0'.
		    // Simply reverting the type to it's default should do the trick.
		    cmd = "restorecon " + mConfigFile + "\n";
		    Log.d(TAG,"Running: '" + cmd);
		    chownOut.writeBytes(cmd);
		    chownOut.writeBytes("sleep 2\n");
		    chownOut.writeBytes("exit\n");
		    chownOut.flush();
		    if (chown.waitFor() == 0) {
			Log.d(TAG,mConfigFile + ": Can read? " + mConfigFile.canRead() + ", Can write? " + mConfigFile.canWrite());
			Log.i(TAG,"Successfully changed the owner of the config file.");
			return true;
		    } else {
			return false;
		    }
		} catch (Exception e) {
		    Log.w(TAG,"Cannot chown config file",e);
		}
	} catch (NameNotFoundException e) {
		// This should not happen!
		Log.w(TAG,"This should not happen",e);
	}
	return false;
    }


    private void generateKeysConfig(Context context) {
        new SyncthingRunnable(context, SyncthingRunnable.Command.generate).run();
    }

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    public URL getWebGuiUrl() {
        try {
            return new URL("https://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse web interface URL", e);
        }
    }

    public String getApiKey() {
        return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
    }

    public String getUserName() {
        return getGuiElement().getElementsByTagName("user").item(0).getTextContent();
    }

    /**
     * Updates the config file.
     *
     * Sets ignorePerms flag to true on every folder, force enables TLS, sets the
     * username/password, and disables weak hash checking.
     */
    @SuppressWarnings("SdCardPath")
    public void updateIfNeeded() {
        Log.i(TAG, "Checking for needed config updates");
        boolean changed = false;
        NodeList folders = mConfig.getDocumentElement().getElementsByTagName("folder");
        for (int i = 0; i < folders.getLength(); i++) {
            Element r = (Element) folders.item(i);
            // Set ignorePerms attribute.
            if (!r.hasAttribute("ignorePerms") ||
                    !Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
                Log.i(TAG, "Set 'ignorePerms' on folder " + r.getAttribute("id"));
                r.setAttribute("ignorePerms", Boolean.toString(true));
                changed = true;
            }

            // Set 'hashers' (see https://github.com/syncthing/syncthing-android/issues/384) on the
            // given folder.
            changed = setConfigElement(r, "hashers", "1") || changed;
        }

        // Enforce TLS.
        Element gui = getGuiElement();
        changed = setConfigElement(gui, "tls", "true") || changed;

        // Set user to "syncthing"
        changed = setConfigElement(gui, "user", "syncthing") || changed;

        // Set password to the API key
        Node password = gui.getElementsByTagName("password").item(0);
        if (password == null) {
            password = mConfig.createElement("password");
            gui.appendChild(password);
        }
        String apikey = getApiKey();
        boolean passwordOk;
        String pw = password.getTextContent();
        passwordOk = !TextUtils.isEmpty(pw) && BCrypt.checkpw(apikey, pw);
        if (!passwordOk) {
            Log.i(TAG, "Updating password");
            password.setTextContent(BCrypt.hashpw(apikey, BCrypt.gensalt(4)));
            changed = true;
        }

        // Disable weak hash benchmark for faster startup.
        // https://github.com/syncthing/syncthing/issues/4348
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        changed = setConfigElement(options, "weakHashSelectionMethod", "never") || changed;

        if (changed) {
            saveChanges();
        }
    }

    private boolean setConfigElement(Element parent, String tagName, String textContent) {
        Node element = parent.getElementsByTagName(tagName).item(0);
        if (element == null) {
            element = mConfig.createElement(tagName);
            parent.appendChild(element);
        }
        if (!textContent.equals(element.getTextContent())) {
            element.setTextContent(textContent);
            return true;
        }
        return false;
    }

    private Element getGuiElement() {
        return (Element) mConfig.getDocumentElement().getElementsByTagName("gui").item(0);
    }

    /**
     * Set model name as device name for Syncthing.
     *
     * We need to iterate through XML nodes manually, as mConfig.getDocumentElement() will also
     * return nested elements inside folder element.
     */
    private void changeLocalDeviceName() {
        NodeList childNodes = mConfig.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeName().equals("device")) {
                ((Element) node).setAttribute("name", Build.MODEL);
            }
        }
        saveChanges();
    }

    /**
     * Change default folder id to camera and path to camera folder path.
     */
    private void changeDefaultFolder() {
        Element folder = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("folder").item(0);
        String model = Build.MODEL
                .replace(" ", "_")
                .toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9_-]", "");
        folder.setAttribute("label", mContext.getString(R.string.default_folder_label));
        folder.setAttribute("id", mContext.getString(R.string.default_folder_id, model));
        folder.setAttribute("path", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        folder.setAttribute("type", "readonly");
        saveChanges();
    }

    /**
     * Writes updated mConfig back to file.
     */
    private void saveChanges() {
		Log.w(TAG,"Can write? "+ mConfigFile.canWrite());
	if (!mConfigFile.canWrite() && !chownConfigFile()) {
	    Log.w(TAG,"Failed to save updated config. Cannot change the owner of the config file.");
	    return;
	}
        try {
            Log.i(TAG, "Writing updated config back to file");
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(mConfig);
		Log.w(TAG,"Can write? "+ mConfigFile.canWrite());
            StreamResult streamResult = new StreamResult(mConfigFile);
            transformer.transform(domSource, streamResult);
        } catch (TransformerException e) {
            Log.w(TAG, "Failed to save updated config", e);
        }
    }

}
