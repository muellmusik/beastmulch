// Classes for implementing 'Plugins' for processing audio
// Plugins here are mono

BMPluginSpec {
	classvar <specs, defaultGuiFunc;
	var <name, <ugenGraphFunc, <specsDict, guiFunc, <>presets, <description, <defaultAttributes;
	var <setupFunc, <cleanupFunc;
	
	*new {|name, ugenGraphFunc, specsDict, guiFunc, presets, description, defaultAttributes, setupFunc, cleanupFunc|
		^super.new.init(name, ugenGraphFunc, specsDict, guiFunc, presets, description, 
			defaultAttributes, setupFunc, cleanupFunc);
	}
	
	init {|argname, argugenGraphFunc, argspecsDict, argguiFunc, argpresets, argdescription, 
		argattributes, argsetupFunc, argcleanupfunc|
		name = argname.asSymbol;
		ugenGraphFunc = argugenGraphFunc;
		specsDict = argspecsDict ? ();
		guiFunc = argguiFunc;
		presets = argpresets ? ();
		description = argdescription ? "";
		defaultAttributes = argattributes ?? { IdentityDictionary.new };
		// by default db specs are converted to linear amp in the gui
		defaultAttributes[\usesLinearAmp].isNil.if({
			defaultAttributes[\usesLinearAmp] = true;
		});
		setupFunc = argsetupFunc;
		cleanupFunc = argcleanupfunc;
		this.class.specs[name] = this;
	}
	
	*initClass {
		// define some plugin specs
		StartUp.add({ 
			specs = IdentityDictionary.new;
			BMPluginSpec('Highpass', 				// name
				{|plugin, input, freq| 	// ugenGraphFunc
					HPF.ar(input, freq);
				}, 								
				(freq: \freq.asSpec),				// specsDict
				nil, 							// default GUI
				(atcs: (freq: 80), tweeters: (freq: 10000)), // presets
				"2nd Order Butterworth Highpass Filter -12db/Oct"
			);
			BMPluginSpec('Lowpass', 				// name
				{|plugin, input, freq| 	// ugenGraphFunc
					LPF.ar(input, freq);
				}, 								
				(freq: \freq.asSpec),				// specsDict
				nil, 							// default GUI
				('very distants': (freq: 4000)), // presets
				"2nd Order Butterworth Lowpass Filter -12db/Oct"
			);
			BMPluginSpec('Bandpass', 				// name
				{|plugin, input, freq, rq| 
					BPF.ar(input, freq, rq);
				}, 								
				(freq: \freq.asSpec, rq: \rq.asSpec.units = " 1/Q"),	
				nil, 						// default GUI
				nil, // no presets
				"2nd Order Butterworth Bandpass Filter"
			);
			BMPluginSpec('Kill DC', 				// name
				{|plugin, input| 	// ugenGraphFunc
					LeakDC.ar(input);
				}, 								
				description: "Cuts through that greasy DC buildup..."
			);
			BMPluginSpec('Delay', 				// name
				{|plugin, input, delayTime| 
					DelayC.ar(input, 2, delayTime);
				},
				(delayTime: ControlSpec(0.0001, 1, \linear, 0, 0.5, units: " secs")), 
				description: "Simple Delay with Cubic Interpolation; 1 second maximum"
			);
			BMPluginSpec('Distance Compensate', 				// name
				{|plugin, input, delayTime| 
					DelayC.ar(input, 2, delayTime);
				},
				(delayTime: ControlSpec(0.0001, 1, \linear, 0, 0.5, units: " secs")), 
				description: "Automatically added Delay with Cubic Interpolation; 1 second maximum"
			);
			BMPluginSpec('FreeVerb', 				// name
				{|plugin, input, mix, roomSize, hfDamp| 
					FreeVerb.ar(input, mix,  roomSize,  hfDamp);
				},
				(
					mix: ControlSpec(0, 1, \linear, 0, 0.25, units: ""),
					roomSize: ControlSpec(0, 1, \linear, 0, 0.5, units: ""),
					hfDamp: ControlSpec(0, 1, \linear, 0, 0.5, units: "")
				), 
				description: "The classic open source Schroeder/Moorer reverb"
			);
			BMPluginSpec('Compander', 				// name
				{|plugin, input, thresh, slopeBelow, slopeAbove, 
				clampTime, relaxTime| 
					Compander.ar(input, input, thresh, slopeBelow, slopeAbove, 
				clampTime, relaxTime);
				},
				(
					thresh: ControlSpec(0.05, 1, \linear, 0, 0.5, units: ""),
					slopeBelow: ControlSpec(0, 10, \linear, 0, 0.5, units: ""),
					slopeAbove: ControlSpec(0, 10, \linear, 0, 0.5, units: ""),
					clampTime: ControlSpec(0, 1, \linear, 0, 0.01, units: "secs"),
					relaxTime: ControlSpec(0, 1, \linear, 0, 0.01, units: "secs")
				), // specsDict
				nil, 							// default GUI
				('noise gate': (thresh: 0.5, slopeBelow: 10, slopeAbove: 1, clampTime: 0.01, relaxTime: 0.01),
				'compressor': (thresh: 0.5, slopeBelow: 1, slopeAbove: 0.5, clampTime: 0.01, relaxTime: 0.01),
				'limiter': (thresh: 0.5, slopeBelow: 1, slopeAbove: 0.1, clampTime: 0.01, relaxTime: 0.01),
				'sustainer': (thresh: 0.5, slopeBelow: 0.1, slopeAbove: 1, clampTime: 0.01, relaxTime: 0.01)
				), // presets
				description: "General purpose (hard-knee) dynamics processor"
			);
			BMPluginSpec('3 Band EQ',
				{|plugin, input, lowFreq, lowGain, midFreq, midrq, midGain
					hiFreq, hiGain| 
					var eqchain;
					eqchain = BLowShelf.ar(input, lowFreq, 1, lowGain);
					eqchain = BPeakEQ.ar(eqchain, midFreq, midrq, midGain);
					BHiShelf.ar(eqchain, hiFreq, 1, hiGain);
				}, 								
				(
					lowFreq: ControlSpec(20, 20000, 'exp', 0, 100, " Hz"),
					lowGain: \boostcut.asSpec,
					midFreq: ControlSpec(20, 20000, 'exp', 0, 1000, " Hz"),
					midGain: \boostcut.asSpec,
					midrq: \rq.asSpec.units = " 1/Q",
					hiFreq: ControlSpec(20, 20000, 'exp', 0, 6000, " Hz"),
					hiGain: \boostcut.asSpec
				),	
				nil, 						// default GUI
				nil, // no presets
				"3 Band EQ based on the BEQSuite. A low shelf, mid parametric, and high shelf implemented with cascading Second Order Section (Biquad) filters."
			);
		// read application directory for source code files of user plugins specs
		// or maybe in app
		});
		defaultGuiFunc = {	|plugin, parent, gui|
			var numSliders, spec, specsDict, guiCtrls, font, labelWidth, virtualCont;
			virtualCont = gui.virtualCont;
			spec = plugin.spec;
			specsDict = plugin.specsDict;
			font = gui.font;
			numSliders = specsDict.size;
			guiCtrls = gui.guiCtrls;
			//displaySpecs = gui.displaySpecs;
			labelWidth = virtualCont.controlNames.collect({|name| 
				name.asString.bounds(font).width
			}).maxItem;
			parent.bounds = parent.bounds.width_(652).height_(numSliders * 24);
			parent.addFlowLayout;
			virtualCont.controlNames.do({|controlName, i|
				var initVal, control, label, displaySpec;
				label = virtualCont.getLabel(i + 1);
				if(label.size == 0, {label = controlName.asString }); 
				control = BMAbstractController.allControls[controlName.asSymbol];
				//displaySpec = control.displaySpec;
				initVal = control.value;
				guiCtrls[i] = EZSlider.new(parent, 
					640@20, 
					label, 
					control.controlSpec,
					{|ez| 
						"setting val: % - %\n".postf(i + 1, ez.value);
						virtualCont.setVal(i + 1, ez.value);
					}, initVal, labelWidth: labelWidth
				);
				guiCtrls[i].numberView.background = Color.white.alpha_(0.4);
				guiCtrls[i].font = font;
				//displaySpecs[i] = displaySpec;
			
			});
		}
	}

	guiFunc {
		^guiFunc ? defaultGuiFunc
	}
	
}

// Class which manages resources for a plugin instance
BMPlugin {
	var <spec, <server, <attributes, <defName, <def, <specsDict;
	var <synth, defaultValues, numControls, synthMappings;
	var <preset;
	var <controller, <name;
	
	*new {|pluginSpecName, server, attributes, name|
		^super.new.init(pluginSpecName, server ? Server.default, attributes, name);
	}
	
	bus { ^controller.bus }
	
	copy {|name|
		var values, newplugin;
		values = this.values;
		newplugin = BMPlugin(this.spec.name, this.server, this.attributes, name);
		values.keysValuesDo({|key, val| newplugin.set(key, val)});
		^newplugin;
	}
	
	values { ^controller.getAllValuesDict }
	
	controlNames { ^controller.controlNames }
	
//	init { |argpluginSpecName, argserver, argattributes|
//		spec = BMPluginSpec.specs[argpluginSpecName.asSymbol];
//		spec.isNil.if({
//			("Plugin spec" + argpluginSpecName + "does not exist!").warn;
//			^nil;
//		});
//		specsDict = spec.specsDict.deepCopy;
//		server = argserver;
//		attributes = spec.defaultAttributes.copy;
//		argattributes.notNil.if({attributes.putAll(argattributes)}); // local settings override
//		spec.setupFunc.value(this);
//		this.makeDef;
//		values = ();
//		controlNames = ();
//		def.allControlNames.reject({|cn| (cn.name == \i_in) || (cn.name == \cfgate)}).do({|cn| 
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
//			controlNames[cn.name] = cn;
//		});
//		defaultValues = values.deepCopy;
//		numControls = def.controls.size; 
//		bus = Bus.control(server, numControls); // this is two larger than it needs to be
//		controlNames.keysValuesDo({|key, cn| 
//			var value;
//			value = values[key];
//			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
//				max(value.size, 1)] ++ value);
//		});
//		mappings = controlNames.values.collectAs({|cn| 
//			[cn.name, ("c" ++ (bus.index + cn.index)).asSymbol];
//		}, Array).flat;
//	}

	*newPluginName {|startName|
		var candidate, i = 0;
		candidate = startName;
		// number em by type
		while({BMAbstractController.allControllers[candidate].notNil }, {
			i = i + 1;
			candidate = (startName.asString ++ "-" ++ i).asSymbol
		});
		^candidate;
	} 

	init { |argpluginSpecName, argserver, argattributes, argName|
		spec = BMPluginSpec.specs[argpluginSpecName.asSymbol];
		spec.isNil.if({
			("Plugin spec" + argpluginSpecName + "does not exist!").warn;
			^nil;
		});
		name = argName ? (spec.name  ++ UniqueID.next).asSymbol;
		// protect against duplicate plugins
		if(BMAbstractController.allControllers[name].notNil, {
			warn("A plugin named " ++ name ++ " already exists");
			name = BMPlugin.newPluginName(name);
			"Using % instead\n\n".postf(name);
		});
		specsDict = spec.specsDict.deepCopy;
		server = argserver;
		attributes = spec.defaultAttributes.copy;
		argattributes.notNil.if({attributes.putAll(argattributes)}); // local settings override
		spec.setupFunc.value(this);
		this.makeDef;
		controller = BMPluginController(this);
		//values = ();
//		controlNames = ();
//		def.allControlNames.reject({|cn| (cn.name == \i_in) || (cn.name == \cfgate)}).do({|cn| 
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
//			controlNames[cn.name] = cn;
//		});
//		defaultValues = values.deepCopy;
//		numControls = def.controls.size; 
//		bus = Bus.control(server, numControls); // this is two larger than it needs to be
//		controlNames.keysValuesDo({|key, cn| 
//			var value;
//			value = values[key];
//			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
//				max(value.size, 1)] ++ value);
//		});
		defaultValues = controller.getAllValuesDict;
		controller.paramNameIndices.keysValuesDo({|cn, ind| 
			synthMappings = synthMappings ++ [cn, ("c" ++ (controller.busIndex + ind)).asSymbol];
		});
	}

	
	makeDef {
		defName = spec.name; 
		if(attributes.notNil, { defName = defName ++ "-" ++ UniqueID.next});
		def = SynthDef(defName, {arg i_in, cfgate = 1;
			var input, out;
			input = In.ar(i_in);
			out = SynthDef.wrap(spec.ugenGraphFunc, nil, [this, input]);
			XOut.ar(i_in, 
				EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), cfgate, 
					doneAction: 2),
				out;
			);
		});
		
	}
	
//	set {|key, value|
//		var cn;
//		cn = controlNames[key];
//		cn.notNil.if({
//			values[key] = value;
//			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
//				max(value.size, 1)] ++ value);
//		}, {("Plugin " ++ spec.name ++ "has no Control named " ++ key).warn });
//	}

	set {|key, value|
		controller.setValByParamName(key, value);
	}
	
//	get {|key|
//		var cn;
//		cn = controlNames[key];
//		cn.notNil.if({
//			^values[key];
//		}, {("Plugin " ++ spec.name ++ "has no Control named " ++ key).warn; ^nil; });
//	}

	get {|key| ^controller.getValByParamName(key) }
	
//	debug {
//		bus.getn(numControls, {|array|
//			"Control Bus values:".postln;
//			controlNames.keysValuesDo({|key, cn| 
//				cn.name.postln;
//				"\t".post;
//				"clientside: ".post;
//				values[cn.name].post;
//				" actual: ".post;
//				array[cn.index].postln;
//			});
//			synth.notNil.if({
//				("\n" ++ spec.name + "plugin synth trace:").postln;
//				synth.trace;
//			});
//		});
//		^("Debugging" + spec.name + "Plugin:\n");
//	}

	debug { ^controller.debug }
	
	preset_{|presetname|
		var psdict;
		psdict = spec.presets[presetname];
		psdict.notNil.if({
			preset = presetname;
			psdict = defaultValues.copy.putAll(psdict); // use defaults for any non-specified
			psdict.keysValuesDo({|key, val| this.set(key, val)});
		}, {("Plugin " ++ spec.name ++ " has no preset named " ++ presetname).warn });
	}
	
	makeSynth {|in, target, addAction=\addToTail|
		(target.asTarget.server != server).if({
			Error("Target server does not match Plugin server.").throw;
		});
		synth.notNil.if({ synth.set(\cfgate, 0); });
		synth = def.play(target, [i_in: in] ++ synthMappings, addAction);
	}
	
//	release { 
//		synth.set(\cfgate, 0); 
//		synth = nil; bus.free; 
//		bus = nil;
//		gui.notNil.if({ gui.close });
//		spec.cleanupFunc.value(this);
//	} // I'm a lame duck...

	release { 
		synth.set(\cfgate, 0); 
		synth = nil; 
		controller.free;
		//gui.notNil.if({ gui.close });
		spec.cleanupFunc.value(this);
	} // I'm a lame duck...
	
	gui {
		//gui.isNil.if({
//			gui = spec.guiFunc.value(this);
//			gui.onClose = gui.onClose.addFunc({ gui = nil });
//		}, {
//			gui.front;
//		});
		^BMPluginGUI(controller)
	}
	
	// post pretty
	printOn { arg stream; stream << this.class.name << "(" <<* [spec.name] << ")" }


}