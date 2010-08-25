// should this support setVal and getVal?
// where does valueArray fit into this?
// would it make more sense to just have the plugin own some controls
// need other BMVirtualController Methods? or make a BMAbstractVirtualController?

BMPluginController : BMAbstractController {
	var plugin, <defControlNameObjs, <controlNames, <values, piSpec, specsDict;
	
	*new { |plugin|
		^super.new.init(plugin).addControlsToIndex;
	}
	
//	init { |argname, argserver, argnumControls|
//		name = argname;
//		server = argserver;
//		numControls = argnumControls;
//
//		// possibly should move this into super
//		valueArray = Array.fill(numControls, {0});
//		labelArray = Array.fill(numControls, {""});
//		bus = Bus.control(server, numControls);
//		busIndex = bus.index;
//		allControllers[name] = this;
//	}
	
	init { |argplugin|
		var def, attributes;
		plugin = argplugin;
		name = plugin.name;
		server = plugin.server;
		attributes = plugin.attributes;
		
		piSpec = plugin.spec;
		specsDict = plugin.specsDict;
		values = ();
		defControlNameObjs = ();
		def = plugin.def;
		def.allControlNames.reject({|cn| (cn.name == \i_in) || (cn.name == \cfgate)}).do({|cn, i| 
			var size, startVal, controlspec;
			size = cn.defaultValue.size;
			controlspec = specsDict[cn.name];
			// take defaults from the control name if no spec supplied. Hmm... maybe not?
			controlspec.isNil.if({Error("No spec for Control:" + cn.name).throw; });
			startVal = controlspec.default;
			(controlspec.units == " dB" && attributes[\usesLinearAmp]).if({ 
				startVal = startVal.dbamp;
			});
			if(size > startVal.size, {startVal = startVal ! size }); // not sure about this
			values[cn.name] = startVal;
			defControlNameObjs[cn.name] = cn;
			controlNames = controlNames.add((name.asString ++ "-" ++ cn.name).asSymbol);
		});
		numControls = def.controls.size; 
		bus = Bus.control(server, numControls); // this is two larger than it needs to be
		busIndex = bus.index;
		valueArray = Array.fill(numControls, {0});
		defControlNameObjs.keysValuesDo({|key, cn| 
			var value;
			valueArray[cn.index] = value = values[key];
			server.sendBundle(nil,(["/c_setn", busIndex + cn.index, 
				max(value.size, 1)] ++ value).postln);
		});
		
		labelArray = Array.fill(numControls, {""});
		
		allControllers[name] = this;
	}

	addControlsToIndex {
		controls = Array.newClear(controlNames.size);
		controlNames.do({|ctrlName, i|
			var control, subName;
			subName = ctrlName.asString.drop(name.size).postln.asSymbol;
			ctrlName = ctrlName.asSymbol;
			control = BMPluginControl(ctrlName, this, i + 1, subName);
			controls[i] = control;
			allControls[ctrlName] = control;
		});
	}
	
	setValByName {|key, value|
		var cn;
		cn = defControlNameObjs[key];
		cn.notNil.if({
			valueArray[cn.index] = value;
			values[key] = value;
			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
				max(value.size, 1)] ++ value);
		}, {("Plugin " ++ name ++ "has no Control named " ++ key).warn });
	}
	
	getValByName {|key|
		var cn;
		cn = defControlNameObjs[key];
		cn.notNil.if({
			^values[key];
		}, {("Plugin " ++ name ++ "has no Control named " ++ key).warn; ^nil; });
	}
	
	debug {
		bus.getn(numControls, {|array|
			"Control Bus values:".postln;
			defControlNameObjs.keysValuesDo({|key, cn| 
				cn.name.postln;
				"\t".post;
				"clientside: ".post;
				values[cn.name].post;
				" actual: ".post;
				array[cn.index].postln;
			});
			plugin.synth.notNil.if({
				("\n" ++ piSpec.name + "plugin synth trace:").postln;
				plugin.synth.trace;
			});
		});
		^("Debugging" + piSpec.name + "Plugin:\n");
	}

	
}

BMPluginControl : BMControl {
	var <subName;
	
	*new {|name, controller, ctrlNum, subName|
		^super.new(name, controller, ctrlNum).init(subName);
	}
	
	init {|argSN|
		subName = argSN;
	}
	
	isMappableControl { ^false }
	
	value {^controller.getValByName(subName) }
	
	value_ {|val| controller.setValByName(subName, val) }
	
}