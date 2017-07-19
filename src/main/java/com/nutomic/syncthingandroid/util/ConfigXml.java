package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

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

        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            mConfig = db.parse(mConfigFile);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new OpenConfigException();
        }

        if (isFirstStart) {
            changeLocalDeviceName();
            changeDefaultFolder();
            generateLoginInfo();
        }
        updateIfNeeded();
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
     * <p/>
     * Sets ignorePerms flag to true on every folder.
     */
    @SuppressWarnings("SdCardPath")
    private void updateIfNeeded() {
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

            if (applyHashers(r)) {
                changed = true;
            }
        }

        // Enforce TLS.
        Element gui = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("gui").item(0);
        boolean tls = Boolean.parseBoolean(gui.getAttribute("tls"));
        if (!tls) {
            Log.i(TAG, "Enforce TLS");
            gui.setAttribute("tls", Boolean.toString(true));
            changed = true;
        }

        // Update old configurations with a randomized password:
        // Set the user to "syncthing" and password to the API key.
        // Configurations with a custom password are not changed.
        String storedPassword = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString("web_gui_password", null);
        if (storedPassword != null) {
            PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                    .remove("web_gui_password")
                    .apply();
            Node password = gui.getElementsByTagName("password").item(0);
            boolean storedPasswordOk;
            try {
                storedPasswordOk = password != null && BCrypt.checkpw(storedPassword, password.getTextContent());
            } catch (RuntimeException e) {
                Log.w(TAG, e);
                storedPasswordOk = false;
            }
            if (storedPasswordOk) {
                Node user = gui.getElementsByTagName("user").item(0);
                if (user != null && user.getTextContent().length() != 0)
                    user.setTextContent("syncthing");
                password.setTextContent(BCrypt.hashpw(getApiKey(), BCrypt.gensalt()));
                changed = true;
            }
        }

        if (changed) {
            saveChanges();
        }
    }

    /**
     * Set 'hashers' (see https://github.com/syncthing/syncthing-android/issues/384) on the
     * given folder.
     *
     * @return True if the XML was changed.
     */
    private boolean applyHashers(Element folder) {
        NodeList childs = folder.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node item = childs.item(i);
            if (item.getNodeName().equals("hashers")) {
                if (item.getTextContent().equals(Integer.toString(0))) {
                    item.setTextContent(Integer.toString(1));
                    return true;
                }
                return false;
            }
        }

        // XML tag does not exist, create it.
        Log.i(TAG, "Set 'hashers' on folder " + folder.getAttribute("id"));
        Element newElem = mConfig.createElement("hashers");
        newElem.setTextContent(Integer.toString(1));
        folder.appendChild(newElem);
        return true;
    }

    private Element getGuiElement() {
        return (Element) mConfig.getDocumentElement()
                .getElementsByTagName("gui").item(0);
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
     * Generates username and password, stores them in config.
     *
     * The API key is used as the password, because we need it in WebGuiActivity.
     * The password in the config is hashed, so we can't use it directly.
     */
    private void generateLoginInfo() {
        Node userNode = mConfig.createElement("user");
        getGuiElement().appendChild(userNode);
        userNode.setTextContent("syncthing");

        Node passwordNode = mConfig.createElement("password");
        getGuiElement().appendChild(passwordNode);
        String hashed = BCrypt.hashpw(getApiKey(), BCrypt.gensalt());
        passwordNode.setTextContent(hashed);
    }

    /**
     * Writes updated mConfig back to file.
     */
    private void saveChanges() {
        try {
            Log.i(TAG, "Writing updated config back to file");
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource domSource = new DOMSource(mConfig);
            StreamResult streamResult = new StreamResult(mConfigFile);
            transformer.transform(domSource, streamResult);
        } catch (TransformerException e) {
            Log.w(TAG, "Failed to save updated config", e);
        }
    }

}
