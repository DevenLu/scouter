/*
 *  Copyright 2015 the original author or authors. 
 *  @https://github.com/scouter-project/scouter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */

package scouter.agent;

import scouter.Version;
import scouter.agent.netio.data.DataProxy;
import scouter.lang.conf.ConfObserver;
import scouter.lang.conf.ConfigValueUtil;
import scouter.lang.counters.CounterConstants;
import scouter.lang.value.ListValue;
import scouter.lang.value.MapValue;
import scouter.net.NetConstants;
import scouter.util.*;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

public class Configure extends Thread {

	private static Configure instance = null;
	public final static String CONF_DIR = "./conf/";

	public final static synchronized Configure getInstance() {
		if (instance == null) {
			instance = new Configure();
			instance.setDaemon(true);
			instance.setName(ThreadUtil.getName(instance));
			instance.start();
		}
		return instance;
	}

	//Network
	public String net_local_udp_ip = null;
	public int net_local_udp_port;
	public String net_collector_ip = "127.0.0.1";
	public int net_collector_udp_port = NetConstants.SERVER_UDP_PORT;
	public int net_collector_tcp_port = NetConstants.SERVER_TCP_PORT;
	public int net_collector_tcp_session_count = 1;
	public int net_collector_tcp_so_timeout_ms = 60000;
	public int net_collector_tcp_connection_timeout_ms = 3000;
	public int net_udp_packet_max_bytes = 60000;

	//Object
	public String obj_type = "";
	public String obj_name = "";

	//Manager
	public StringSet mgr_log_ignore_ids = new StringSet();

	//Counter
	public boolean counter_enabled = true;
	public String counter_object_registry_path = "/tmp/scouter";

	//Log
	public boolean log_udp_object = false;
	public boolean log_rotation_enalbed = true;
	public String log_dir = "./logs";
	public int log_keep_days = 365;

	//Disk
	public boolean disk_alert_enabled = true;
	public int disk_warning_pct = 70;
	public int disk_fatal_pct = 90;
	public StringSet disk_ignore_names = new StringSet();

	//Cpu
	public boolean cpu_alert_enabled = true;
	public long cpu_check_period_ms = 300000;
	public long cpu_alert_interval_ms = 30000;
	public int cpu_warning_pct = 70;
	public int cpu_fatal_pct = 90;
	public int cpu_warning_history = 3;
	public int cpu_fatal_history = 3;
	public int _cpu_value_avg_sec = 10;

	//Memory
	public boolean mem_alert_enabled = false;
	public long mem_alert_interval_ms = 30000;
	public int mem_warning_pct = 80;
	public int mem_fatal_pct = 90;

	//internal variables
	private int objHash;
	private String objName;

	private Configure() {
		Properties p = new Properties();
		Map args = new HashMap();
		args.putAll(System.getenv());
		args.putAll(System.getProperties());
		p.putAll(args);
		this.property = p;
		reload(false);
	}

	private Configure(boolean b) {
	}

	private long last_load_time = -1;
	public Properties property = new Properties();

	private boolean running = true;

	public void run() {
		Logger.println("Version " + Version.getAgentFullVersion());
		long dateUnit = DateUtil.getDateUnit();
		while (running) {
			reload(false);
			// Text Data Reset..
			long nowUnit = DateUtil.getDateUnit();
			if (dateUnit != nowUnit) {
				dateUnit = nowUnit;
				DataProxy.reset();
			}
			ThreadUtil.sleep(3000);
		}
	}

	private File propertyFile;

	public File getPropertyFile() {
		if (propertyFile != null) {
			return propertyFile;
		}
		String s = System.getProperty("scouter.config", CONF_DIR + "scouter.conf");
		propertyFile = new File(s.trim());
		return propertyFile;
	}

	long last_check = 0;

	public synchronized boolean reload(boolean force) {
		long now = System.currentTimeMillis();
		if (force == false && now < last_check + 3000)
			return false;
		last_check = now;

		File file = getPropertyFile();

		if (file.lastModified() == last_load_time) {
			return false;
		}

		last_load_time = file.lastModified();
		Properties temp = new Properties();

		if (file.canRead()) {
			FileInputStream in = null;
			try {
				in = new FileInputStream(file);
				temp.load(in);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				FileUtil.close(in);
			}
		}
		property = ConfigValueUtil.replaceSysProp(temp);
		apply();
		ConfObserver.run();
		return true;
	}

	private void apply() {

		this.net_udp_packet_max_bytes = getInt("net_udp_packet_max_bytes", getInt("udp.packet.max", 60000));
		this.mgr_log_ignore_ids = getStringSet("mgr_log_ignore_ids", ",");

		this.net_local_udp_ip = getValue("net_local_udp_ip");
		this.net_local_udp_port = getInt("net_local_udp_port", 0);

		this.net_collector_ip = getValue("net_collector_ip", getValue("server.addr", "127.0.0.1"));
		this.net_collector_udp_port = getInt("net_collector_udp_port", getInt("server.port", NetConstants.SERVER_UDP_PORT));
		this.net_collector_tcp_port = getInt("net_collector_tcp_port", getInt("server.port", NetConstants.SERVER_TCP_PORT));
		this.net_collector_tcp_session_count = getInt("net_collector_tcp_session_count", 1, 1);
		this.net_collector_tcp_connection_timeout_ms = getInt("net_collector_tcp_connection_timeout_ms", 3000);
		this.net_collector_tcp_so_timeout_ms = getInt("net_collector_tcp_so_timeout_ms", 60000);

		this.counter_enabled = getBoolean("counter_enabled", true);
		this.log_udp_object = getBoolean("log_udp_object", false);

		this.log_dir = getValue("log_dir", "./logs");
		this.log_rotation_enalbed = getBoolean("log_rotation_enalbed", true);
		this.log_keep_days = getInt("log_keep_days", 365);

		this.counter_object_registry_path = getValue("counter_object_registry_path", "/tmp/scouter");

		this.disk_alert_enabled = getBoolean("disk_alert_enabled", true);
		this.disk_warning_pct = getInt("disk_warning_pct", 70);
		this.disk_fatal_pct = getInt("disk_fatal_pct", 90);
		this.disk_ignore_names = getStringSet("disk_ignore_names", ",");

		this.cpu_alert_enabled = getBoolean("cpu_alert_enabled", true);
		this.cpu_check_period_ms = getLong("cpu_check_period_ms", 300000);
		this.cpu_alert_interval_ms = getLong("cpu_alert_interval_ms", 30000);
		this.cpu_warning_pct = getInt("cpu_warning_pct", 70);
		this.cpu_fatal_pct = getInt("cpu_fatal_pct", 90);
		this.cpu_warning_history = getInt("cpu_warning_history", 3);
		this.cpu_fatal_history = getInt("cpu_fatal_history", 3);
		this._cpu_value_avg_sec = getInt("_cpu_value_avg_sec", 10);

		this.mem_alert_enabled = getBoolean("mem_alert_enabled", false);
		this.mem_alert_interval_ms = getLong("mem_alert_interval_ms", 30000);
		this.mem_warning_pct = getInt("mem_warning_pct", 80);
		this.mem_fatal_pct = getInt("mem_fatal_pct", 90);
		
		
		resetObjInfo();
	}

	private StringSet getStringSet(String key, String deli) {
		StringSet set = new StringSet();
		String v = getValue(key);
		if (v != null) {
			String[] vv = StringUtil.split(v, deli);
			for (String x : vv) {
				x = StringUtil.trimToEmpty(x);
				if (x.length() > 0)
					set.put(x);
			}
		}
		return set;
	}

	public synchronized void resetObjInfo() {
		String detected = CounterConstants.HOST;
		if (SystemUtil.IS_LINUX) {
			detected = CounterConstants.LINUX;
		} else if (SystemUtil.IS_WINDOWS) {
			detected = CounterConstants.WINDOWS;
		} else if (SystemUtil.IS_MAC_OSX) {
			detected = CounterConstants.OSX;
		} else if (SystemUtil.IS_AIX) {
			detected = CounterConstants.AIX;
		} else if (SystemUtil.IS_HP_UX) {
			detected = CounterConstants.HPUX;
		}
		this.obj_type = getValue("obj_type", detected);
		this.obj_name = getValue("obj_name", SysJMX.getHostName());

		this.objName = "/" + this.obj_name;
		this.objHash = HashUtil.hash(objName);

	}

	public String getValue(String key) {
		return StringUtil.trim(property.getProperty(key));
	}

	public String getValue(String key, String def) {
		return StringUtil.trim(property.getProperty(key, def));
	}

	public int getInt(String key, int def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Integer.parseInt(v);
		} catch (Exception e) {
		}
		return def;
	}

	public int getInt(String key, int def, int min) {
		try {
			String v = getValue(key);
			if (v != null) {
				return Math.max(Integer.parseInt(v), min);
			}
		} catch (Exception e) {
		}
		return Math.max(def, min);
	}

	public long getLong(String key, long def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Long.parseLong(v);
		} catch (Exception e) {
		}
		return def;
	}

	public boolean getBoolean(String key, boolean def) {
		try {
			String v = getValue(key);
			if (v != null)
				return Boolean.parseBoolean(v);
		} catch (Exception e) {
		}
		return def;
	}

	public int getObjHash() {
		return this.objHash;
	}

	public String getObjName() {
		return this.objName;
	}

	public String loadText() {
		File file = getPropertyFile();
		InputStream fin = null;
		try {
			fin = new FileInputStream(file);
			byte[] buff = FileUtil.readAll(fin);
			return new String(buff);
		} catch (Exception e) {
		} finally {
			FileUtil.close(fin);
		}
		return null;
	}

	public boolean saveText(String text) {
		File file = getPropertyFile();
		OutputStream out = null;
		try {
			if (file.getParentFile().exists() == false) {
				file.getParentFile().mkdirs();
			}
			out = new FileOutputStream(file);
			out.write(text.getBytes());
			return true;
		} catch (Exception e) {
		} finally {
			FileUtil.close(out);
		}
		return false;
	}

	public void printConfig() {
		System.out.println("Configure -Dscouter.config=" + propertyFile);
	}

	private static HashSet<String> ignoreSet = new HashSet<String>();

	static {
		ignoreSet.add("property");
	}

	public MapValue getKeyValueInfo() {
		StringKeyLinkedMap<Object> defMap = ConfigValueUtil.getConfigDefault(new Configure(true));
		StringKeyLinkedMap<Object> curMap = ConfigValueUtil.getConfigDefault(this);
		MapValue m = new MapValue();
		ListValue nameList = m.newList("key");
		ListValue valueList = m.newList("value");
		ListValue defList = m.newList("default");

		StringEnumer enu = defMap.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextString();
			if (ignoreSet.contains(key))
				continue;
			nameList.add(key);
			valueList.add(ConfigValueUtil.toValue(curMap.get(key)));
			defList.add(ConfigValueUtil.toValue(defMap.get(key)));
		}

		return m;
	}

	public static void main(String[] args) {
		StringKeyLinkedMap<Object> defMap = ConfigValueUtil.getConfigDefault(new Configure(true));
		StringEnumer enu = defMap.keys();
		while (enu.hasMoreElements()) {
			String key = enu.nextString();
			if (ignoreSet.contains(key))
				continue;
			System.out.println(key + " : " + ConfigValueUtil.toValue(defMap.get(key)));
		}
	}
}