package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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

    private static final String[] REMOVE_ANNOUNCE_IPS = new String[] {
            "udp4://194.126.249.5:22026",
            "udp6://[2001:470:28:4d6::5]:22026",
            "https://194.126.249.5/?id=SR7AARM-TCBUZ5O-VFAXY4D-CECGSDE-3Q6IZ4G-XG7AH75-OBIXJQV-QJ6NLQA",
            "https://45.55.230.38/?id=AQEHEO2-XOS7QRA-X2COH5K-PO6OPVA-EWOSEGO-KZFMD32-XJ4ZV46-CUUVKAS",
            "https://128.199.95.124/?id=7WT2BVR-FX62ZOW-TNVVW25-6AHFJGD-XEXQSBW-VO3MPL2-JBTLL4T-P4572Q4",
            "https://[2001:470:28:4d6::5]/?id=SR7AARM-TCBUZ5O-VFAXY4D-CECGSDE-3Q6IZ4G-XG7AH75-OBIXJQV-QJ6NLQA",
            "https://[2604:a880:800:10::182:a001]/?id=AQEHEO2-XOS7QRA-X2COH5K-PO6OPVA-EWOSEGO-KZFMD32-XJ4ZV46-CUUVKAS",
            "https://[2400:6180:0:d0::d9:d001]/?id=7WT2BVR-FX62ZOW-TNVVW25-6AHFJGD-XEXQSBW-VO3MPL2-JBTLL4T-P4572Q4",
    };

    /**
     * File in the config folder that contains configuration.
     */
    public static final String CONFIG_FILE = "config.xml";

    private static final String INVALID_CONFIG_FILE = "config.xml.invalid";

    private static final int OPEN_CONFIG_MAX_TRIES = 10;

    private final Context mContext;

    private File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) throws OpenConfigException {
        mContext = context;
        mConfigFile = getConfigFile(context);
        boolean isFirstStart = !mConfigFile.exists();
        if (isFirstStart) {
            Log.i(TAG, "App started for the first time. Generating keys and config.");
            generateKeysConfig(context);
        }

        for (int i = 0; i < OPEN_CONFIG_MAX_TRIES && mConfig == null; i++) {
            try {
                DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                mConfig = db.parse(mConfigFile);
            } catch (SAXException | ParserConfigurationException | IOException e) {
                Log.w(TAG, "Failed to open config, moving to " + INVALID_CONFIG_FILE +
                        " and creating blank config");
                File dest = new File(mConfigFile.getParent(), INVALID_CONFIG_FILE);
                if (dest.exists())
                    dest.delete();
                mConfigFile.renameTo(dest);
                generateKeysConfig(context);
                isFirstStart = true;
                mConfigFile = getConfigFile(context);
            }
        }
        if (mConfig == null)
            throw new OpenConfigException();

        if (isFirstStart) {
            changeDefaultFolder();
        }
        updateIfNeeded();
    }

    private void generateKeysConfig(Context context) {
        new SyncthingRunnable(context, SyncthingRunnable.Command.generate).run();
    }

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    public String getWebGuiUrl() {
        return "https://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent();
    }

    public String getApiKey() {
        return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
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
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);

        if (restoreDefaultAnnounceServers(options)) {
            changed = true;
        }

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

        if (changed) {
            saveChanges();
        }
    }

    /**
     * Removes our hardcoded announce server IPs, and adds the 'default' value again.
     *
     * For compatibility with 0.7.4
     */
    private boolean restoreDefaultAnnounceServers(Element options) {
        // Hardcode default globalAnnounceServer ip.
        NodeList globalAnnounceServers = options.getElementsByTagName("globalAnnounceServer");
        boolean announceServersRemoved = false;
        for (int i = 0; i < globalAnnounceServers.getLength(); i++) {
            Node announce = globalAnnounceServers.item(i);
            if (Arrays.asList(REMOVE_ANNOUNCE_IPS).contains(announce.getTextContent())) {
                options.removeChild(announce);
                announceServersRemoved = true;
            }
        }

        if (announceServersRemoved) {
            Log.i(TAG, "Replacing globalAnnounceServer address with ip");
            Element newAnnounce = mConfig.createElement("globalAnnounceServer");
            newAnnounce.setTextContent("default");
            options.appendChild(newAnnounce);
            return true;
        }
        return false;
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
     * Change default folder id to camera and path to camera folder path.
     */
    public void changeDefaultFolder() {
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
