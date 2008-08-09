// plugin, numInputs, numOutputs, inputs passed to function as prepend args
// inputs is an array of In Ugens reading from buses derived from a BMInOutArray or subarray
// inputs could be 0

// synthdefFunc is a function suitable for use with SynthDef:wrap

// guiFunc creates a window to control a plugin synth.
// guiFunc will be passed the plugin itself so that specs, and current vals can be derived
// A default GUI can be created if one is not supplied.

// presets is an IdentityDict of IdentityDicts: name->IdentityDict[\control->value...]
// for the moment presets cannot be created at runtime
// eventually this will be possible, and they will be stored in the piece preset?

// attributes allows for arbitrary user data for constructing the synthdef and gui

// setupFunc is a setup function that must be completed before the plugin is made
// it will be passed the plugin instance
// it can store any objects for further reference in attributes for use by the plugin or graphFunc
// if needed you can wrap the plugin:new in a routine and sync before calling makeSynth

// cleanupFunc allows for any heavy resources to be cleaned up after the plugin is removed.
// e.g a Buffer, which would have been stored in attributes

// description is human readable text (String)

// ---------

// To do:
// Does default gui work?

BMMultichannelPluginSpec {
	classvar <specs, defaultGuiFunc;
	var <name, <ugenGraphFunc, <specsDict, guiFunc, <>presets, <description, <defaultAttributes;
	var <minInputs, <minOutputs, <maxInputs, <maxOutputs; //nil for max = unlimited
	var setupFunc, cleanupFunc;
	
	*new {|name, ugenGraphFunc, specsDict, guiFunc, presets, description, defaultAttributes, 
		inRange, outRange, setupFunc, cleanupFunc| // ranges are [min, max]
		^super.new.init(name, ugenGraphFunc, specsDict, guiFunc, presets, description, 
			defaultAttributes, inRange, outRange, setupFunc, cleanupFunc);
	}
	
	init {|argname, argugenGraphFunc, argspecsDict, argguiFunc, argpresets, argdescription, 
		argattributes, arginRange, argoutRange, argsetupFunc, argcleanupfunc|
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
		arginRange = arginRange ?? { [1, inf] };
		argoutRange = argoutRange ?? { [1, inf] };
		minInputs = arginRange[0];
		maxInputs = arginRange[1];
		minOutputs = argoutRange[0];
		maxOutputs = argoutRange[1];
		setupFunc = argsetupFunc;
		cleanupFunc = argcleanupfunc;
		this.class.specs[name] = this;
	}
	
	*initClass {
		// define some plugin specs
		StartUp.add({ 
			specs = ();
			BMMultichannelPluginSpec('3D VBAP Panner', 				// name
				{|plugin, numInputs, numOutputs, inputs, azimuth, elevation, spread, azimuthLag| 	// ugenGraphFunc
					VBAP.ar(numOutputs, inputs[0], plugin.attributes[\buffer], azimuth.circleRamp(azimuthLag), elevation, spread);
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " degrees"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " degrees"].asSpec, 
				spread: [0, 100, 'lin', 0.0, 2, " %"].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " seconds"].asSpec
				),				// specsDict
				nil, 							// default GUI
				(atcs: (freq: 80), tweeters: (freq: 10000)), // presets
				"Mono input 3D Vector Base Amplitude Panner",
				nil, 							// defaultAttributes
				nil,								// inRange
				nil,								// outRange
				{|plugin| 
					var speakers;
					speakers = plugin.outputs.collect({|out|
						out.isBMSpeaker.not.if({
							"VBAP output not a speaker".error;
							^false;
						});
						[out.azi, out.ele];
					});
					speakers = VBAPSpeakerArray(3, speakers);
					plugin.attributes[\buffer] = 
						Buffer.loadCollection(plugin.server, speakers.getSetsAndMatrices);
				},								// setupFunc
				{|plugin|
					plugin.attributes[\buffer].free;
				}								// cleanupFunc
			);
		
		// read application directory for source code files of user plugins specs
		// or maybe in app
		});
		defaultGuiFunc = {|plugin|
			var numSliders, spec, window, presetMenu, sliders;
			spec = plugin.spec;
			numSliders = spec.specsDict.size;
			window = SCWindow.new("Plugin:" + spec.name, 
				Rect(300, 300, 552, (numSliders + 1) * 24 + 24), false); // 508
			window.view.decorator = FlowLayout(window.view.bounds);
			window.view.background = Color.rand.alpha_(0.3);
			sliders = ();
			spec.specsDict.sortedKeysValuesDo({|key, cspec|
				var initVal;
				initVal = plugin.get(key);
				(cspec.units == " dB" && plugin.attributes[\usesLinearAmp]).if({ 
					initVal = initVal.ampdb;
				});
				sliders[key] = EZSlider.new(window, 500@20, key.asString, cspec,
					{|ez| var setVal;
						setVal = ez.value;
						(cspec.units == " dB" && plugin.attributes[\usesLinearAmp]).if({ 
							setVal = setVal.dbamp;
						});
						plugin.set(key, setVal);
					}, initVal
				);
				sliders[key].numberView.boxColor = Color.white.alpha_(0.4);
				SCStaticText(window, Rect(0,0,40,20)).string_(cspec.units);
			
			});
			window.view.decorator.nextLine.shift(10, 10);
			presetMenu = SCPopUpMenu(window, Rect(0, 0, 100, 20));
			presetMenu.items = ["presets", "-"] ++ spec.presets.keys;
			presetMenu.action = {
				if(presetMenu.value > 1, {
					plugin.preset_(presetMenu.items[presetMenu.value].asSymbol);
					sliders.keysValuesDo({|key, slid| 
						var newVal;
						newVal = plugin.get(key);
						(slid.controlSpec.units == " dB" 
							&& plugin.attributes[\usesLinearAmp]).if({ 
							newVal = newVal.ampdb;
						});
						slid.value = newVal;
					});
				});
			};
			window.front;
		}
	}
	
	guiFunc { ^guiFunc ? defaultGuiFunc }
	
}


// To do:
// Fix new
// Fix init with sync func
// At the moment, this does sync func before making the def. Is that right?
// Otherwise we'd need to store info about heavy resources rather than hard coding it
// I'm not sure if there's a case where we actually need a reply.

// Class which manages resources for a plugin instance
BMMultichannelPlugin {
	var <spec, <server, <attributes, <defName, <def;
	var <synth, <values, defaultValues, <bus, numControls, controlNames, mappings;
	var <preset;
	var <numInputs, <numOutputs, <inputs, <outputs;
	
	*new {|pluginSpecName, inArray, outArray, server, attributes|
		^super.new.init(pluginSpecName, inArray, outArray, server ? Server.default, attributes);
	}
	
	init { |argpluginSpecName, argins, argouts, argserver, argattributes|
		spec = BMPluginSpec.specs[argpluginSpecName.asSymbol];
		inputs = argins;
		outputs = argouts;
		numInputs = inputs.size;
		numOutputs = outputs.size;
		// check size and bail
		if(numInputs.inclusivelyBetween(spec.minInputs, spec.maxInputs).not || 
			numOutputs.inclusivelyBetween(spec.minOutputs, spec.maxOutputs).not, {
			("Input or output array not within allowable size range for plugin" 
				+ spec.name).error;
			^nil;	
		});
		server = argserver;
		attributes = spec.defaultAttributes.copy;
		argattributes.notNil.if({attributes.putAll(argattributes)}); // local settings override
		
		spec.setupFunc.value(this);
		this.makeDef;
		def.send(server);
		values = ();
		controlNames = ();
		def.allControlNames.reject({|cn| (cn.name == \i_in) || (cn.name == \cfgate)}).do({|cn| 
			var size, startVal, controlspec;
			size = cn.defaultValue.size;
			controlspec = spec.specsDict[cn.name];
			// take defaults from the control name if no spec supplied. Hmm... maybe not?
			controlspec.isNil.if({Error("No spec for Control:" + cn.name).throw; });
			startVal = controlspec.default;
			(controlspec.units == " dB" && attributes[\usesLinearAmp]).if({ 
				startVal = startVal.dbamp;
			});
			if(size > startVal.size, {startVal = startVal ! size }); // not sure about this
			values[cn.name] = startVal;
			controlNames[cn.name] = cn;
		});
		defaultValues = values.deepCopy;
		numControls = def.controls.size; 
		bus = Bus.control(server, numControls); // this is two larger than it needs to be
		controlNames.keysValuesDo({|key, cn| 
			var value;
			value = values[key];
			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
				max(value.size, 1)] ++ value);
		});
		mappings = controlNames.values.collectAs({|cn| 
			[cn.name, ("c" ++ (bus.index + cn.index)).asSymbol];
		}, Array).flat;
		CmdPeriod.add(this);
	}
	
	makeDef {
		defName = spec.name ++ UniqueID.next; 
		if(attributes.notNil, { defName = defName ++ "-" ++ UniqueID.next});
		def = SynthDef(defName, {arg cfgate = 1;
			var input, out, env;
			input = In.ar(inputs);
			(input.size == 1).if({input = input[0];});
			out = SynthDef.wrap(spec.ugenGraphFunc, nil, [this, numInputs, numOutputs, input]);
			
			// fade in and out, release
			env = EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), cfgate, 
				doneAction: 2);
			if(out.size != numOutputs, {
				"Plugin output does not match size of output array.".warn;
			});
			// if sizes don't match take the first outputs
			out.do({|chan, i| XOut.ar(outputs[i], env, chan);});
		});
		
	}
	
	set {|key, value|
		var cn;
		cn = controlNames[key];
		cn.notNil.if({
			values[key] = value;
			server.sendBundle(nil,["/c_setn", bus.index + cn.index, 
				max(value.size, 1)] ++ value);
		}, {("Plugin " ++ spec.name ++ "has no Control named " ++ key).warn });
	}
	
	get {|key|
		var cn;
		cn = controlNames[key];
		cn.notNil.if({
			^values[key];
		}, {("Plugin " ++ spec.name ++ "has no Control named " ++ key).warn; ^nil; });
	}
	
	debug {
		bus.getn(numControls, {|array|
			"Control Bus values:".postln;
			controlNames.keysValuesDo({|key, cn| 
				cn.name.postln;
				"\t".post;
				"clientside: ".post;
				values[cn.name].post;
				" actual: ".post;
				array[cn.index].postln;
			});
			synth.notNil.if({
				("\n" ++ spec.name + "plugin synth trace:").postln;
				synth.trace;
			});
		});
		^("Debugging" + spec.name + "Plugin:\n");
	}
	
	preset_{|presetname|
		var psdict;
		psdict = spec.presets[presetname];
		psdict.notNil.if({
			preset = presetname;
			psdict = defaultValues.copy.putAll(psdict); // use defaults for any non-specified
			psdict.keysValuesDo({|key, val| this.set(key, val)});
		}, {("Plugin " ++ spec.name ++ " has no preset named " ++ presetname).warn });
	}
	
	makeSynth {|target, addAction=\addToTail|
		(target.asTarget.server != server).if({
			Error("Target server does not match Plugin server.").throw;
		});
		synth.notNil.if({ synth.set(\cfgate, 0); });
		synth = Synth(defName, mappings, target, addAction);
	}
	
	release { 
		synth.set(\cfgate, 0); 
		synth = nil; bus.free; 
		bus = nil;
		spec.cleanupFunc.value(this);
		CmdPeriod.remove(this);
	} // I'm a lame duck...
	
	cmdPeriod { 
		synth = nil; 
		bus.free; 
		CmdPeriod.remove(this);
	}
	
	gui {
		spec.guiFunc.value(this);
	}
	
	copy {
		var values, newplugin;
		values = this.values;
		newplugin = BMPlugin(this.spec.name, this.inputs, this.outputs, this.server, this.attributes);
		values.keysValuesDo({|key, val| newplugin.set(key, val)});
		^newplugin;
	}

}


//BMAbstractAudioChainElement {
//	classvar <allChainElements;
//	var <ins, <outs, <inNames, <outNames; // in the default case the getters return nil, as an element need not have both ins and outs
//	var <group, <>server, <name, <callCmdPeriod = true;

//------- To do:
// fix mappings

BMMultichannelPluginsRack : BMAbstractAudioChainElement {
	var <plugins;
	
//	*new {|target, input|
//		^super.new.init(target, input);
//	}

	
//	init {|argtarget, arginput|
//		target = argtarget.asGroup;
//		server = target.server;
//		input = arginput;
//		
//		plugins = List.new;
//		target.server.makeBundle(nil, {
//			this.sendDef;
//			server.sync;
//			this.makeNodes; // first time only trim...
//		});
//	}
	
	*new { |ins, group, server, name|
		^super.new.init(ins, group, server ? Server.default, name);
		// default name is class
	}
	
	init {|argins, arggroup, argserver, argname|
		ins = argins;
		outs = argins;
		group = arggroup;
		server = argserver;
		name = argname  ? this.makeName;
		inNames = ins.keys;
		outNames = outs.keys;
		if(group.isNil, {this.makeGroup});
		plugins = List.new;
		CmdPeriod.add(this);
		allChainElements[name] = this;
	}

	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.new(inAudioArray, group, server, name);
	}
	
	makeGroup { group = Group.tail(server); }
	
	clear { 
		plugins = List.new;
		this.makeNodes;
	}
	
	mappings { 
		var dict;
		dict = IdentityDictionary.new;
		dict[\plugins] = plugins.collect({|plugin|
			// could be a problem if pluginspec changes in the meantime
			[plugin.spec.name, plugin.inputs, plugin.outputs, plugin.attributes, plugin.values];
		}); // these are in order
		^dict;
	}
	
	mappings_ { |dict| 
		this.plugins.do({|plugin| plugin.release;});
		plugins = List.new;
		dict[\plugins].do({|pluginArray|
			var plugin;
			plugin = BMMultichannelPlugin(pluginArray[0], pluginArray[1], pluginArray[2], server, 
				pluginArray[3]);
			this.addPlugin(plugin);
			pluginArray[4].keysValuesDo({|k, v| plugin.set(k, v)});
		});
		this.changed;
	}
	
//	target_{|argtarget|
//		target = argtarget.asGroup; 
//		(target.asTarget.server != server).if({
//			Error("Target server does not match Plugins' server.").throw;
//		});
//	
//	}
	
	makeNodes { 
		server.makeBundle(nil, {
			//group = Group.new(target);
			plugins.do({|plgin|
				plgin.makeSynth(group, \addToTail);
			});
		});
		this.changed;
	}
	
	addPlugin {|plugin|
		plugins.add(plugin);
		server.makeBundle(nil, {
			server.sync; // wait for the plugin's def to arrive...
			plugin.makeSynth(group, \addToTail);
			// added at end, no need to reset order on server
			this.changed;
		});
	}
	
	removePlugin {|index|
		var toBeRemoved;
		toBeRemoved = plugins.removeAt(index);
		toBeRemoved.release; // free synth and resources
		// just removed, no need to reset order on server
		this.changed;
	}
	
	movePluginUp {|index|
		if(index > 0, {
			plugins.swap(index, index - 1);
			this.resetOrder;
			this.changed(\moveUp);
		});
	}

	movePluginDown {|index|
		if(index < (plugins.size -1), {
			plugins.swap(index, index + 1);
			this.resetOrder;
			this.changed(\moveDown);
		});
	}
	
	resetOrder {
		server.makeBundle(nil, {
			plugins.do({|plgin|
				plgin.synth.moveToTail(group);
			});
		});
	}
	
}