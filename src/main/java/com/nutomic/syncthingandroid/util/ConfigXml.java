package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.nutomic.syncthingandroid.syncthing.SyncthingRunnable;
import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

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

    private static final String TAG = "ConfigXml";

    /**
     * File in the config folder that contains configuration.
     */
    public static final String CONFIG_FILE = "config.xml";

    private static final String INVALID_CONFIG_FILE = "config.xml.invalid";

    private File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) {
        mConfigFile = getConfigFile(context);
        boolean isFirstStart = !mConfigFile.exists();
        if (isFirstStart) {
            Log.i(TAG, "App started for the first time. Generating keys and config.");
            generateKeysConfig(context);
        }

        // This could cause an infinite loop, maybe we should add a counter, too.
        do {
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
        } while (mConfig == null);

        if (isFirstStart) {
            changeDefaultFolder();
        }
        updateIfNeeded();
    }

    private void generateKeysConfig(Context context) {
        new SyncthingRunnable(context, context.getApplicationInfo().dataDir + "/" +
                SyncthingService.BINARY_NAME + " -generate='" + context.getFilesDir() + "'")
                .run();
    }

    public static File getConfigFile(Context context) {
        return new File(context.getFilesDir(), CONFIG_FILE);
    }

    public String getWebGuiUrl() {
        return "http://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent();
    }

    public String getApiKey() {
        return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
    }

    /**
     * Updates the config file.
     * <p/>
     * Coming from 0.2.0 and earlier, globalAnnounceServer value "announce.syncthing.net:22025" is
     * replaced with "194.126.249.5:22025" (as domain resolve is broken).
     * <p/>
     * Coming from 0.3.0 and earlier, the ignorePerms flag is set to true on every folder.
     */
    @SuppressWarnings("SdCardPath")
    private void updateIfNeeded() {
        Log.i(TAG, "Checking for needed config updates");
        boolean changed = false;
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        Element gui = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("gui").item(0);

        // Hardcode default globalAnnounceServer ip.
        NodeList globalAnnounceServer = options.getElementsByTagName("globalAnnounceServer");
        for (int i = 0; i < globalAnnounceServer.getLength(); i++) {
            Element g = (Element) globalAnnounceServer.item(i);
            if (g.getTextContent().equals("udp4://announce.syncthing.net:22026")) {
                Log.i(TAG, "Replacing globalAnnounceServer address with ip");
                g.setTextContent("udp4://194.126.249.5:22026");
                changed = true;
            }
            if (g.getTextContent().equals("udp6://announce-v6.syncthing.net:22026")) {
                Log.i(TAG, "Replacing IPv6 globalAnnounceServer address with ip");
                g.setTextContent("udp6://[2001:470:28:4d6::5]:22026");
                changed = true;
            }
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

            // Replace /sdcard/ in folder path with proper path.
            String dir = r.getAttribute("path");
            if (dir.startsWith("/sdcard")) {
                String newDir = dir.replace("/sdcard",
                        Environment.getExternalStorageDirectory().getAbsolutePath());
                r.setAttribute("directory", newDir);
                changed = true;
            }

            // HACK: Create .stfolders in all folders if it does not exist.
            // This is not the best place to put it, but it's only temporary.
            try {
                new File(dir, ".stfolder").createNewFile();
            } catch (IOException e) {
                // This might happen if the device is not mounted.
                Log.i(TAG, "Failed to create .stversions in " + dir, e);
            }

            if (applyLenientMTimes(r)) {
                changed = true;
            }
        }

        if (changed) {
            saveChanges();
        }
    }

    /**
     * Set 'lenientMtimes' (see https://github.com/syncthing/syncthing/issues/831) on the
     * given folder.
     *
     * @return True if the XML was changed.
     */
    private boolean applyLenientMTimes(Element folder) {
        NodeList childs = folder.getChildNodes();
        for (int i = 0; i < childs.getLength(); i++) {
            Node item = childs.item(i);
            if (item.getNodeName().equals("lenientMtimes")) {
                if (item.getTextContent().equals(Boolean.toString(false))) {
                    item.setTextContent(Boolean.toString(true));
                    return true;
                }
                return false;
            }
        }

        // XML tag does not exist, create it.
        Log.i(TAG, "Set 'lenientMtimes' on folder " + folder.getAttribute("id"));
        Element newElem = mConfig.createElement("lenientMtimes");
        newElem.setTextContent(Boolean.toString(true));
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
        folder.setAttribute("id", "camera");
        folder.setAttribute("path", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        folder.setAttribute("ro", "true");
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
