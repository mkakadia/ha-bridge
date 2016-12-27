package com.bwssystems.harmony;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bwssystems.HABridge.BridgeSettingsDescriptor;
import com.bwssystems.HABridge.DeviceMapTypes;
import com.bwssystems.HABridge.Home;
import com.bwssystems.HABridge.IpList;
import com.bwssystems.HABridge.NamedIP;
import com.bwssystems.HABridge.api.CallItem;
import com.bwssystems.HABridge.api.hue.DeviceState;
import com.bwssystems.HABridge.api.hue.StateChangeBody;
import com.bwssystems.HABridge.dao.DeviceDescriptor;
import com.bwssystems.HABridge.hue.MultiCommandUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.whistlingfish.harmony.config.Activity;
import net.whistlingfish.harmony.config.Device;

public class HarmonyHome implements Home {
    private static final Logger log = LoggerFactory.getLogger(HarmonyHome.class);
	private Map<String, HarmonyServer> hubs;
	private Boolean isDevMode;
	private Boolean validHarmony;
	private Gson aGsonHandler;

	public HarmonyHome(BridgeSettingsDescriptor bridgeSettings) {
		super();
		createHome(bridgeSettings);
	}

	@Override
	public void closeHome() {
		if(isDevMode || hubs == null)
			return;
		Iterator<String> keys = hubs.keySet().iterator();
		while(keys.hasNext()) {
			String key = keys.next();
			hubs.get(key).getMyHarmony().shutdown();
		}
		
		hubs = null;
	}

	public HarmonyHandler getHarmonyHandler(String aName) {
		HarmonyHandler aHandler = null;
		if(aName == null || aName.equals("")) {
			aName = "default";
		}

		if(hubs.get(aName) == null) {
			Set<String> keys = hubs.keySet();
			if(!keys.isEmpty()) {
				aHandler = hubs.get(keys.toArray()[0]).getMyHarmony();
			}
			else
				aHandler = null;
		}
		else
			aHandler = hubs.get(aName).getMyHarmony();
		return aHandler;
	}
	
	public List<HarmonyActivity> getActivities() {
		Iterator<String> keys = hubs.keySet().iterator();
		ArrayList<HarmonyActivity> activityList = new ArrayList<HarmonyActivity>();
		while(keys.hasNext()) {
			String key = keys.next();
			Iterator<Activity> activities = hubs.get(key).getMyHarmony().getActivities().iterator();
			while(activities.hasNext()) {
				HarmonyActivity anActivity = new HarmonyActivity();
				anActivity.setActivity(activities.next());
				anActivity.setHub(key);
				activityList.add(anActivity);
			}
		}
		return activityList;
	}
	public List<HarmonyActivity> getCurrentActivities() {
		Iterator<String> keys = hubs.keySet().iterator();
		ArrayList<HarmonyActivity> activityList = new ArrayList<HarmonyActivity>();
		while(keys.hasNext()) {
			String key = keys.next();
			Activity theActivity = hubs.get(key).getMyHarmony().getCurrentActivity();
			HarmonyActivity anActivity = new HarmonyActivity();
			anActivity.setActivity(theActivity);
			anActivity.setHub(key);
			activityList.add(anActivity);
		}
		return activityList;
	}
	public List<HarmonyDevice> getDevices() {
		Iterator<String> keys = hubs.keySet().iterator();
		ArrayList<HarmonyDevice> deviceList = new ArrayList<HarmonyDevice>();
		while(keys.hasNext()) {
			String key = keys.next();
			Iterator<Device> devices = hubs.get(key).getMyHarmony().getDevices().iterator();
			while(devices.hasNext()) {
				HarmonyDevice aDevice = new HarmonyDevice();
				aDevice.setDevice(devices.next());
				aDevice.setHub(key);
				deviceList.add(aDevice);
			}
		}
		return deviceList;
	}

	@Override
	public String deviceHandler(CallItem anItem, MultiCommandUtil aMultiUtil, String lightId, int iterationCount,
			DeviceState state, StateChangeBody theStateChanges, boolean stateHasBri, boolean stateHasBriInc, DeviceDescriptor device, String body) {
		String responseString = null;
		log.debug("executing HUE api request to change " + anItem.getType() + " to Harmony: " + device.getTargetDevice());
		if(!validHarmony) {
			log.warn("Should not get here, no harmony configured");
			responseString = "[{\"error\":{\"type\": 6, \"address\": \"/lights/" + lightId
					+ "\",\"description\": \"Should not get here, no harmony configured\", \"parameter\": \"/lights/"
					+ lightId + "state\"}}]";			
		} else {
			if(anItem.getType().trim().equalsIgnoreCase(DeviceMapTypes.HARMONY_ACTIVITY[DeviceMapTypes.typeIndex]))
			{
				RunActivity anActivity = aGsonHandler.fromJson(anItem.getItem().toString(), RunActivity.class);
				HarmonyHandler myHarmony = getHarmonyHandler(device.getTargetDevice());
				if (myHarmony == null) {
					log.warn("Should not get here, no harmony hub available");
					responseString = "[{\"error\":{\"type\": 6, \"address\": \"/lights/" + lightId
							+ "\",\"description\": \"Should not get here, no harmony hub available\", \"parameter\": \"/lights/"
							+ lightId + "state\"}}]";
				} else {
					for (int x = 0; x < aMultiUtil.getSetCount(); x++) {
						if (x > 0 || iterationCount > 0) {
							try {
								Thread.sleep(aMultiUtil.getTheDelay());
							} catch (InterruptedException e) {
								// ignore
							}
						}
						if (anItem.getDelay() != null && anItem.getDelay() > 0)
							aMultiUtil.setTheDelay(anItem.getDelay());
						else
							aMultiUtil.setTheDelay(aMultiUtil.getDelayDefault());
						myHarmony.startActivity(anActivity);
					}
				}
			} else if(anItem.getType().trim().equalsIgnoreCase(DeviceMapTypes.HARMONY_BUTTON[DeviceMapTypes.typeIndex])) {
				String url = anItem.getItem().toString();
				if (url.substring(0, 1).equalsIgnoreCase("{")) {
					url = "[" + url + "]";
				}
				ButtonPress[] deviceButtons = aGsonHandler.fromJson(url, ButtonPress[].class);
				HarmonyHandler myHarmony = getHarmonyHandler(device.getTargetDevice());
				if (myHarmony == null) {
					log.warn("Should not get here, no harmony hub available");
					responseString = "[{\"error\":{\"type\": 6, \"address\": \"/lights/" + lightId
							+ "\",\"description\": \"Should not get here, no harmony hub available\", \"parameter\": \"/lights/"
							+ lightId + "state\"}}]";
				} else {
		        	Integer theCount = 1;
	        		for(int z = 0; z < deviceButtons.length; z++) {
		        		if(deviceButtons[z].getCount() != null && deviceButtons[z].getCount() > 0)
		        			theCount = deviceButtons[z].getCount();
		        		else
		        			theCount = aMultiUtil.getSetCount();
		        		for(int y = 0; y < theCount; y++) {
		        			if( y > 0 || z > 0) {
									try {
										Thread.sleep(aMultiUtil.getTheDelay());
									} catch (InterruptedException e) {
										// ignore
									}
								}
								if (anItem.getDelay() != null && anItem.getDelay() > 0)
									aMultiUtil.setTheDelay(anItem.getDelay());
								else
									aMultiUtil.setTheDelay(aMultiUtil.getDelayDefault());
		    	        	log.debug("pressing button: " + deviceButtons[z].getDevice() + " - " + deviceButtons[z].getButton() + " - iteration: " + String.valueOf(z) + " - count: " + String.valueOf(y));
		        			myHarmony.pressButton(deviceButtons[z]);
		        		}
	        		}
				}
			}
		}
		return responseString;
	}

	@Override
	public Home createHome(BridgeSettingsDescriptor bridgeSettings) {
        isDevMode = Boolean.parseBoolean(System.getProperty("dev.mode", "false"));
        validHarmony = bridgeSettings.isValidHarmony();
		if(!validHarmony && !isDevMode) {
			log.debug("No valid Harmony config");
		} else {
			hubs = new HashMap<String, HarmonyServer>();
			aGsonHandler =
					new GsonBuilder()
					.create();
			if(isDevMode) {
				NamedIP devModeIp = new NamedIP();
				devModeIp.setIp("10.10.10.10");
				devModeIp.setName("devMode");
				List<NamedIP> theList = new ArrayList<NamedIP>();
				theList.add(devModeIp);
				IpList thedevList = new IpList();
				thedevList.setDevices(theList);
				bridgeSettings.setHarmonyAddress(thedevList);
			}
			Iterator<NamedIP> theList = bridgeSettings.getHarmonyAddress().getDevices().iterator();
			while(theList.hasNext() && validHarmony) {
				NamedIP aHub = theList.next();
		      	try {
		      		hubs.put(aHub.getName(), HarmonyServer.setup(bridgeSettings, isDevMode, aHub));
				} catch (Exception e) {
			        log.error("Cannot get harmony client (" + aHub.getName() + ") setup, Exiting with message: " + e.getMessage(), e);
			        validHarmony = false;
				}
			}
		}
		return this;
	}

	@Override
	public Object getItems(String type) {
		if(type.equalsIgnoreCase(DeviceMapTypes.HARMONY_ACTIVITY[DeviceMapTypes.resourceIndex]))
			return getActivities();
		if(type.equalsIgnoreCase(DeviceMapTypes.HARMONY_BUTTON[DeviceMapTypes.resourceIndex]))
			return getDevices();
		if(type.equalsIgnoreCase("current_activity"))
			return getCurrentActivities();
		return null;
	}
}
