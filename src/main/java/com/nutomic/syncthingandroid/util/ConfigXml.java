package com.nutomic.syncthingandroid.util;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

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

    private File mConfigFile;

    private Document mConfig;

    public ConfigXml(Context context) {
        mConfigFile = getConfigFile(context);
        if (!mConfigFile.exists()) {
            copyDefaultConfig(context);
        }
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            mConfig = db.parse(mConfigFile);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new RuntimeException("Failed to open config file", e);
        }
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
    public void updateIfNeeded() {
        Log.i(TAG, "Checking for needed config updates");
        boolean changed = false;
        Element options = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("options").item(0);
        Element gui = (Element) mConfig.getDocumentElement()
                .getElementsByTagName("gui").item(0);

        // Create an API key if it does not exist.
        if (gui.getElementsByTagName("apikey").getLength() == 0) {
            Log.i(TAG, "Initializing API key with random string");
            char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
            StringBuilder sb = new StringBuilder();
            Random random = new Random();
            for (int i = 0; i < 20; i++) {
                sb.append(chars[random.nextInt(chars.length)]);
            }
            Element apiKey = mConfig.createElement("apikey");
            apiKey.setTextContent(sb.toString());
            gui.appendChild(apiKey);
            changed = true;
        }

        // Hardcode default globalAnnounceServer ip.
        Element globalAnnounceServer = (Element)
                options.getElementsByTagName("globalAnnounceServer").item(0);
        if (globalAnnounceServer.getTextContent().equals("announce.syncthing.net:22025")) {
            Log.i(TAG, "Replacing globalAnnounceServer host with ip");
            globalAnnounceServer.setTextContent("194.126.249.5:22025");
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
                boolean exists = new File(dir, ".stfolder").createNewFile();
                Log.d(TAG, dir + Boolean.toString(exists));
            } catch (IOException e) {
                // This might happen if the device is not mounted.
                Log.i(TAG, "Failed to create .stversions in " + dir, e);
            }

            if (applyLenientMTimes(r)) {
                changed = true;
            }
        }

        // Change global announce server port to 22026 for syncthing v0.9.0.
        if (globalAnnounceServer.getTextContent().equals("194.126.249.5:22025")) {
            Log.i(TAG, "Changing announce server port for v0.9.0");
            globalAnnounceServer.setTextContent("194.126.249.5:22026");
            changed = true;
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
     * Creates a folder for the default camera folder.
     */
    public void createCameraFolder() {
        Element cameraFolder = mConfig.createElement("folder");
        cameraFolder.setAttribute("id", "camera");
        cameraFolder.setAttribute("directory", Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());
        cameraFolder.setAttribute("ro", "true");
        cameraFolder.setAttribute("ignorePerms", "true");
        mConfig.getDocumentElement().appendChild(cameraFolder);

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

    /**
     * Copies the default config file from res/raw/config_default.xml to (data folder)/config.xml.
     */
    private void copyDefaultConfig(Context context) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getResources().openRawResource(R.raw.config_default);
            out = new FileOutputStream(mConfigFile);
            byte[] buff = new byte[1024];
            int read;

            while ((read = in.read(buff)) > 0) {
                out.write(buff, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config file", e);
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close stream while copying config", e);
            }
        }
    }

}
