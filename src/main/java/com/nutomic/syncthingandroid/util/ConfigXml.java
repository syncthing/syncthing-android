package com.nutomic.syncthingandroid.util;

import android.util.Log;
import android.util.Pair;

import com.nutomic.syncthingandroid.syncthing.SyncthingService;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
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

	private File mConfigFile;

	private Document mConfig;

	public ConfigXml(File configFile) {
		mConfigFile = configFile;
		try {
			DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			mConfig = db.parse(configFile);
		} catch (SAXException e) {
			throw new RuntimeException("Failed to parse config file", e);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("Failed to parse config file", e);
		} catch (IOException e) {
			throw new RuntimeException("Failed to open config file", e);
		}
	}

	public String getWebGuiUrl() {
		return "http://" + getGuiElement().getElementsByTagName("address").item(0).getTextContent();
	}

	public String getApiKey() {
		return getGuiElement().getElementsByTagName("apikey").item(0).getTextContent();
	}

	/**
	 * Updates the config file.
	 *
	 * Coming from 0.2.0 and earlier, globalAnnounceServer value "announce.syncthing.net:22025" is
	 * replaced with "194.126.249.5:22025" (as domain resolve is broken).
	 *
	 * Coming from 0.3.0 and earlier, the ignorePerms flag is set to true on every repository.
	 */
	public void update() {
		try {
			Log.i(TAG, "Checking for needed config updates");
			boolean changed = false;
			Element options = (Element) mConfig.getDocumentElement()
					.getElementsByTagName("options").item(0);
			Element gui = (Element)	mConfig.getDocumentElement()
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

			// Set ignorePerms attribute.
			NodeList repos = mConfig.getDocumentElement().getElementsByTagName("repository");
			for (int i = 0; i < repos.getLength(); i++) {
				Element r = (Element) repos.item(i);
				if (!r.hasAttribute("ignorePerms") ||
						!Boolean.parseBoolean(r.getAttribute("ignorePerms"))) {
					Log.i(TAG, "Set 'ignorePerms' on repository " + r.getAttribute("id"));
					r.setAttribute("ignorePerms", Boolean.toString(true));
					changed = true;
				}
			}

			// Write the changes back to file.
			if (changed) {
				Log.i(TAG, "Writing updated config back to file");
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
				DOMSource domSource = new DOMSource(mConfig);
				StreamResult streamResult = new StreamResult(mConfigFile);
				transformer.transform(domSource, streamResult);
			}
		}
		catch (TransformerException e) {
			Log.w(TAG, "Failed to save updated config", e);
		}
	}

	private Element getGuiElement() {
		return (Element) mConfig.getDocumentElement()
				.getElementsByTagName("gui").item(0);
	}

}
