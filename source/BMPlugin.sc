// Classes for implementing 'Plugins' for processing audio
// Plugins can be mono or output multichannel
// Their outputs need not match their inputs

// synthdefFunc is a function suitable for use with SynthDef:wrap
// which is passed args |plugin, numChannels, in| and returns a ugen for output
// Multichannel output plugins should send the output to a private bus
// and mute or passthrough the source as appropriate.

// guiFunc creates a window to control a plugin synth.
// guiFunc will be passed the plugin itself so that specs, and current vals can be derived
// A default GUI can be created if one is not supplied.

// presets is an IdentityDict of IdentityDicts: name->IdentityDict[\control->value...]
// for the moment presets cannot be created at runtime
// eventually this will be possible, and they will be stored in the piece preset?

// attributes allows for arbitrary user data for constructing the synthdef and gui

// description is human readable text (String)

// For now at least plugins map to control busses
// This should allow for an easy later extension to allow rt control with controllers

BMPluginSpec {
	classvar <specs;
	var <name, <ugenGraphFunc, <specsDict, <guiFunc, <>presets, <description, <defaultAttributes;
	
	*new {|name, ugenGraphFunc, specsDict, guiFunc, presets, description, defaultAttributes|
		^super.new.init(name, ugenGraphFunc, specsDict, guiFunc, presets, description, 
			defaultAttributes);
	}
	
	init {|argname, argugenGraphFunc, argspecsDict, argguiFunc, argpresets, argdescription, 
		argattributes|
		name = argname.asSymbol;
		ugenGraphFunc = argugenGraphFunc;
		specsDict = argspecsDict;
		guiFunc = argguiFunc;
		presets = argpresets;
		description = argdescription;
		defaultAttributes = argattributes ?? { IdentityDictionary.new };
		
		this.class.specs[name] = this;
	}
	
	*initClass {
		// define some plugin specs
		StartUp.add({ 
			specs = ();
			BMPluginSpec('highpass', 				// name
				{|plugin, numChannels, input, freq| 	// ugenGraphFunc
					HPF.ar(input, freq);
				}, 								
				(freq: \freq.asSpec),				// specsDict
				nil, 							// default GUI
				(atcs: (freq: 80), tweeters: (freq: 10000)), // presets
				"2nd Order Butterworth Highpass Filter -12db/Oct"
			);
			BMPluginSpec('lowpass', 				// name
				{|plugin, numChannels, input, freq| 	// ugenGraphFunc
					LPF.ar(input, freq);
				}, 								
				(freq: \freq.asSpec),				// specsDict
				nil, 							// default GUI
				('very distants': (freq: 4000)), // presets
				"2nd Order Butterworth Lowpass Filter -12db/Oct"
			);
			BMPluginSpec('bandpass', 				// name
				{|plugin, numChannels, input, freq, rq| 
					BPF.ar(input, freq, rq);
				}, 								
				(freq: \freq.asSpec, rq: \rq.asSpec),	
				nil, 						// default GUI
				nil, // no presets
				"2nd Order Butterworth Bandpass Filter"
			);
			BMPluginSpec('Kill DC', 				// name
				{|plugin, numChannels, input| 	// ugenGraphFunc
					LeakDC.ar(input);
				}, 								
				description: "Cuts through that greasy DC buildup..."
			);
			BMPluginSpec('3 Band EQ',
				{|plugin, numChannels, input, lowFreq, lowGain, midFreq, midrq, midGain
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
					midrq: \rq.asSpec,
					hiFreq: ControlSpec(20, 20000, 'exp', 0, 6000, " Hz"),
					hiGain: \boostcut.asSpec
				),	
				nil, 						// default GUI
				nil, // no presets
				"3 Band EQ based on the BEQSuite. A low shelf, mid parametric, and high shelf implemented with cascading Second Order Section (Biquad) filters"
			);
		// read application directory for source code files of user plugins specs
		});
	}
	
}

// Class which manages resources for a plugin instance
BMPlugin {
	var <spec, <numChannels = 1, <server, <attributes, <defName, <def;
	var <synth, <values, defaultValues, <bus, numControls, controlNames, mappings;
	var <preset;
	
	*new {|pluginSpecName, numChannels = 1, server, attributes|
		^super.new.init(pluginSpecName, numChannels = 1, server ? Server.default, attributes);
	}
	
	init { |argpluginSpecName, argnumChannels, argserver, argattributes|
		spec = BMPluginSpec.spec[argpluginSpecName.asSymbol];
		numChannels = argnumChannels;
		server = argserver;
		attributes = spec.defaultAttributes.copy.putAll(argattributes); // local settings override
		this.makeDef;
		def.send(server);
		values = ();
		controlNames = ();
		def.allControlNames.do({|cn| 
			var size, startVal, controlspec;
			size = cn.defaultValue.size;
			controlspec = spec.specsDict[cn.name];
			// take defaults from the control name if no spec supplied. Hmm... maybe not?
			startVal = controlspec.notNil.if({controlspec.default;},{cn.defaultValue});
			if(size > startVal.size, {startVal = startVal ! size }); // not sure about this
			values[cn.name] = startVal;
			controlNames[cn.name] = cn;
		});
		defaultValues = values.deepCopy;
		numControls = def.controls.size;
		bus = Bus.control(server, numControls);
		controlNames.keysValuesDo({|key, cn| 
			var value;
			value = values[key];
			server.sendBundle(nil,["/c_setn", bus.index + cn.index, max(value.size, 1)] 
				++ value);
		});
		mappings = controlNames.values.collectAs({|cn| 
			[cn.name, ("c" ++ (bus.index + cn.index)).asSymbol];
		}, Array).flat;
	}
	
	makeDef {
		defName = spec.name ++ numChannels; 
		if(attributes.notNil, { defName = defName ++ "-" ++ UniqueID.next});
		def = SynthDef(defName, {arg i_in, cfgate = 1;
			var input, out;
			input = In.ar(i_in);
			out = SynthDef.wrap(spec.ugenGraphFunc, nil, [this, numChannels, input]);
			XOut.ar(i_in, 
				EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), cfgate, 
					doneAction: 2),
				out;
			);
		});
		
	}
	
	set {|key, value|
		var cn;
		cn = controlNames[key];
		cn.notNil.if({
			values[key] = value;
			server.sendBundle(nil,["/c_setn", bus.index + cn.index, max(value.size, 1)] 
				++ value);
		}, {("Plugin " ++ spec.name ++ "has no Control named " ++ key).warn });
	}
	
	preset_{|presetname|
		var psdict;
		psdict = spec.presets[presetname];
		psdict.notNil.if({
			preset = presetname;
			psdict = defaultValues.copy.putAll(psdict); // use defaults for any non-specified
			psdict.keysValues.do({|key, val| this.set(key, val)});
		}, {("Plugin " ++ spec.name ++ "has no preset named " ++ presetname).warn });
	}
	
	// args here is an IdentityDictionary or an Event
	makeSynth {|target, addAction=\addToTail|
		synth.isNil.if({this.release});
		synth = Synth(defName, mappings, target, addAction);
	}
	
	release { synth.release; synth = nil; }

}