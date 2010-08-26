// should this support setVal and getVal?
// where does valueArray fit into this?
// would it make more sense to just have the plugin own some controls
// need other BMVirtualController Methods? or make a BMAbstractVirtualController?

// controlNames is global name, ctrllr.name-param
// paramNameIndices is param only (subName)

BMPluginController : BMAbstractController {
	var <plugin, <paramNameIndices, <controlNames, piSpec, specsDict;
	
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
	
//	init { |argplugin|
//		var def, attributes;
//		plugin = argplugin;
//		name = plugin.name;
//		server = plugin.server;
//		attributes = plugin.attributes;
//		
//		piSpec = plugin.spec;
//		specsDict = plugin.specsDict;
//		values = ();
//		paramNameIndices = ();
//		def = plugin.def;
//		def.allControlNames.reject({|cn| (cn.name == \i_in) || (cn.name == \cfgate)}).do({|cn, i| 
//			var size, startVal, controlspec;
//			size = cn.defaultValue.size;
//			controlspec = specsDict[cn.name];
//			// take defaults from the control name if no spec supplied. Hmm... maybe not?
//			controlspec.isNil.if({Error("No spec for Control:" + cn.name).throw; });
//			startVal = controlspec.default;
//			(controlspec.units == " dB" && attributes[\usesLinearAmp]).if({ 
//				startVal = startVal.dbamp;
//			});
//			if(size > startVal.size, {startVal = startVal ! size }); // not sure about this
//			values[cn.name] = startVal;
//			paramNameIndices[cn.name] = cn;
//			controlNames = controlNames.add((name.asString ++ "-" ++ cn.name).asSymbol);
//		});
//		numControls = def.controls.size; 
//		bus = Bus.control(server, numControls); // this is two larger than it needs to be
//		busIndex = bus.index;
//		valueArray = Array.fill(numControls, {0});
//		paramNameIndices.keysValuesDo({|key, cn| 
//			var value;
//			valueArray[cn.index] = value = values[key];
//			server.sendBundle(nil,(["/c_setn", busIndex + cn.index, 
//				max(value.size, 1)] ++ value).postln);
//		});
//		
//		labelArray = Array.fill(numControls, {""});
//		
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
		paramNameIndices = IdentityDictionary.new;
		def = piSpec.ugenGraphFunc.def;
		numControls = def.argNames.size - 2; // ignore plugin and input
		valueArray = Array.newClear(numControls);
		def.argNames.copyToEnd(2).do({|cn, i| // ignore plugin and input
			var size, startVal, controlspec;
			size = def.prototypeFrame[i + 2].size; // check default values
			controlspec = specsDict[cn];
			// take defaults from the control name if no spec supplied. Hmm... maybe not?
			controlspec.isNil.if({Error("No spec for Control:" + cn).throw; });
			startVal = controlspec.default;
			(controlspec.units == " dB" && attributes[\usesLinearAmp]).if({ 
				startVal = startVal.dbamp;
			});
			if(size > startVal.size, {startVal = startVal ! size }); // not sure about this
			valueArray[i] = startVal;
			paramNameIndices[cn] = i;
			controlNames = controlNames.add((name.asString ++ "-" ++ cn).asSymbol);
		}); 
		bus = Bus.control(server, numControls);
		busIndex = bus.index;
		valueArray.do({|value, i|
			server.sendBundle(nil,(["/c_setn", busIndex + i, max(value.size, 1)] ++ value).postln);
		});
		
		labelArray = def.argNames.copyToEnd(2).collect(_.asString);
		
		allControllers[name] = this;
	}
	
	addControlsToIndex {
		controls = Array.newClear(paramNameIndices.size);
		paramNameIndices.keysValuesDo({|subName, ind|
			var control, ctrlName;
			"subName: %\n".postf(subName);
			//subName = ctrlName.asString.drop(name.size).asSymbol.postln;
			ctrlName = controlNames[ind];
			control = BMPluginControl(ctrlName, this, ind + 1, subName);
			control.mappedTo_(plugin, specsDict[subName]);
			controls[ind] = control;
			allControls[ctrlName] = control;
		});
	}
	
	setVal { |controlNum, val| 
		var chan;
		chan = controlNum - 1;
		server.sendBundle(nil,["/c_setn", busIndex + chan, max(val.size, 1)] ++ val);
		valueArray[chan] = val; 
		this.changed(\controlVal, chan, val);
	}
	
	setValByParamName {|key, value|
		var ind;
		ind = paramNameIndices[key];
		ind.notNil.if({
			this.setVal(ind + 1, value);
		}, {("Plugin " ++ name ++ "has no Control named " ++ key).warn });
	}
	
	getVal { |controlNum|
		^valueArray[controlNum -1];
	}
	
	getValByParamName {|key|
		var ind;
		ind = paramNameIndices[key];
		ind.notNil.if({
			^valueArray[ind];
		}, {("Plugin " ++ name ++ "has no Control named " ++ key).warn; ^nil; });
	}
	
	getAllValues { ^valueArray; }
	
	getAllValuesDict {
		^paramNameIndices.collect({|ind, key| valueArray[ind] });
	}
	
	setAllValues {|array|  array.do({|item, i| this.setVal(i + 1, item);}); }
	
	setLabel { |fader, name|
		labelArray[fader - 1] = name;
		this.changed(\label, fader - 1, name);
	}
	
	getLabel { |fader| ^labelArray[fader-1] }
	
	getAllLabels {  ^labelArray  }
	
	setAllLabels {|array| array.do({|item, i| this.setLabel(i+1, item);}); }

	acceptsAutomation { ^true }
	
	debug {
		bus.getn(numControls, {|array|
			"Control Bus values:".postln;
			paramNameIndices.keysValuesDo({|key, ind| 
				key.postln;
				"\t".post;
				"clientside: ".post;
				valueArray[ind].post;
				" actual: ".post;
				array[ind].postln;
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
	
	displaySpec { ^BMNoOpSpec } // experimental
	
}