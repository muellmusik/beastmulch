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
// Does default gui work? Yes
// Copy specsDict in plugin so that they can be adjusted according to inputs, etc.?
// 	- eg in VBAP clip elevation based on available speakers

BMMultichannelPluginSpec {
	classvar <specs, defaultGuiFunc;
	var <name, <ugenGraphFunc, <specsDict, guiFunc, <>presets, <description, <defaultAttributes;
	var <minInputs, <minOutputs, <maxInputs, <maxOutputs; //nil for max = unlimited
	var <setupFunc, <cleanupFunc;
	
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
					VBAP.ar(numOutputs, inputs, plugin.attributes[\buffer], azimuth.circleRamp(azimuthLag), elevation, spread);
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec, 
				spread: [0, 100, 'lin', 0.0, 2, " %"].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " sec"].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"Mono input 3D Vector Base Amplitude Panner",
				nil, 							// defaultAttributes
				nil,								// inRange
				nil,								// outRange
				{|plugin| 
					var speakers;
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"VBAP output not a speaker".error;
							^false;
						});
						[out.value.azi, out.value.ele];
					}, Array);
					speakers = VBAPSpeakerArray(3, speakers);
					plugin.attributes[\buffer] = 
						Buffer.loadCollection(plugin.server, speakers.getSetsAndMatrices);
				},								// setupFunc
				{|plugin|
					plugin.attributes[\buffer].free;
				}								// cleanupFunc
			);
			BMMultichannelPluginSpec('Stereo 3D VBAP Panner', 				// name
				{|plugin, numInputs, numOutputs, inputs, azimuth, elevation, spread, azimuthLag, 
					azimuthWidth, elevationWidth| 	// ugenGraphFunc
					var azdev, eldev;
					azdev = azimuthWidth * 0.5;
					eldev = elevationWidth * 0.5;
					Mix(VBAP.ar(numOutputs, inputs, plugin.attributes[\buffer], 
						azimuth.circleRamp(azimuthLag) + [azdev.neg, azdev], 
						elevation + [eldev.neg, eldev], spread));
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec, 
				spread: [0, 100, 'lin', 0.0, 2, " %"].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " sec"].asSpec,
				azimuthWidth: [0, 360, 'lin', 0.0,  60, " deg"].asSpec,
				elevationWidth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"Stereo input 3D Vector Base Amplitude Panner",
				nil, 							// defaultAttributes
				nil,								// inRange
				nil,								// outRange
				{|plugin| 
					var speakers;
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"VBAP output not a speaker".error;
							^false;
						});
						[out.value.azi, out.value.ele];
					}, Array);
					speakers = VBAPSpeakerArray(3, speakers);
					plugin.attributes[\buffer] = 
						Buffer.loadCollection(plugin.server, speakers.getSetsAndMatrices);
				},								// setupFunc
				{|plugin|
					plugin.attributes[\buffer].free;
				}								// cleanupFunc
			);
			BMMultichannelPluginSpec('B-Format Decoder', 				// name
				{|plugin, numInputs, numOutputs, inputs| // ugenGraphFunc
					// w, x, y, z
					
					BFDecode1.ar(inputs[0], inputs[1], inputs[2], inputs[3], 						plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				nil,				// specsDict
				nil, 							// default GUI
				nil, // presets
				"3D B-Format Ambisonic Decoder; input order w, x, y, z",
				nil, 							// defaultAttributes
				[4, 4],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop.postln;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('B-Format Decoder Comp', 				// name
				{|plugin, numInputs, numOutputs, inputs| // ugenGraphFunc
					// w, x, y, z
					
					BFDecode1.ar1(inputs[0], inputs[1], inputs[2], inputs[3], 						plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1],
						plugin.attributes[\maxDist],
						plugin.attributes[\speakersCoords][2]
					);
				}, 								
				nil,				// specsDict
				nil, 							// default GUI
				nil, // presets
				"3D B-Format Ambisonic Decoder; delay compensated. Input order is w, x, y, z.",
				nil, 							// defaultAttributes
				[4, 4],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad, out.value.rad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
					plugin.attributes[\maxDist] = speakers[2].maxItem;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('3D Ambi Panner', // name
				{|plugin, numInputs, numOutputs, inputs, azimuth, elevation, rho, azimuthLag| // ugenGraphFunc
					var w, x, y, z;
					var atorad = (2 * pi / 360);
					#w, x, y, z = BFEncode1.ar(inputs, azimuth.circleRamp(azimuthLag) * atorad, 
						elevation * atorad, rho);
					BFDecode1.ar(w, x, y, z, plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " sec"].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"1st Order Mono input 3D Ambisonic Panner",
				nil, 							// defaultAttributes
				[1, 1],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('Stereo 3D Ambi Panner', // name
				{|plugin, numInputs, numOutputs, inputs, azimuth, width, elevation, rho, azimuthLag| // ugenGraphFunc
					var w, x, y, z;
					var atorad = (2 * pi / 360);
					#w, x, y, z = BFEncodeSter.ar(inputs[0], inputs[1], azimuth.circleRamp(azimuthLag) * atorad, 
						width * atorad,
						elevation * atorad, rho);
					BFDecode1.ar(w, x, y, z, plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec,
				width: [0, 360, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " sec"].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"1st Order Stereo input 3D Ambisonic Panner; inputs are L, R",
				nil, 							// defaultAttributes
				[2, 2],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('FMH Ambi Panner', // name
				{|plugin, numInputs, numOutputs, inputs, azimuth, elevation, rho, azimuthLag| // ugenGraphFunc
					var w, x, y, z, r, s, t, u, v;
					var atorad = (2 * pi / 360);
					#w, x, y, z, r, s, t, u, v = FMHEncode1.ar(inputs, azimuth.circleRamp(azimuthLag).neg * atorad, 
						elevation * atorad, rho);
					FMHDecode1.ar(w, x, y, z, r, s, t, u, v, 
						plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(azimuth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec,
				azimuthLag: [0, 1, 'lin', 0.0, 0.1, " sec"].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"2nd Order Mono input 3D Ambisonic Panner",
				nil, 							// defaultAttributes
				[1, 1],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);

			BMMultichannelPluginSpec('3D VBAP Auto Panner', 				// name
				{|plugin, numInputs, numOutputs, inputs, elevation, spread, speed| 	// ugenGraphFunc
					var azimuth;
					azimuth = LFSaw.kr(speed.reciprocal).range(-180, 180);
					VBAP.ar(numOutputs, inputs, plugin.attributes[\buffer], azimuth, elevation, spread);
				}, 								
				(speed: [0.1, 20, 'lin', 0.0,  5, " sec"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec, 
				spread: [0, 100, 'lin', 0.0, 2, " %"].asSpec
				),				// specsDict
				nil, 							// default GUI
				nil, // presets
				"Mono input 3D Vector Base Amplitude Auto Panner",
				nil, 							// defaultAttributes
				nil,								// inRange
				nil,								// outRange
				{|plugin| 
					var speakers;
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"VBAP output not a speaker".error;
							^false;
						});
						[out.value.azi, out.value.ele];
					}, Array);
					speakers = VBAPSpeakerArray(3, speakers);
					plugin.attributes[\buffer] = 
						Buffer.loadCollection(plugin.server, speakers.getSetsAndMatrices);
				},								// setupFunc
				{|plugin|
					plugin.attributes[\buffer].free;
				}								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('Stereo Auto 3D VBAP Panner', 				// name
				{|plugin, numInputs, numOutputs, inputs, elevation, spread, speed, 
					azimuthWidth, elevationWidth| 	// ugenGraphFunc
					var azdev, eldev;
					var azimuth;
					azimuth = LFSaw.kr(speed.reciprocal).range(-180, 180);
					azdev = azimuthWidth * 0.5;
					eldev = elevationWidth * 0.5;
					Mix(VBAP.ar(numOutputs, inputs, plugin.attributes[\buffer], 
						azimuth + [azdev.neg, azdev], 
						elevation + [eldev.neg, eldev], spread));
				}, 								
				(speed: [0.1, 20, 'lin', 0.0,  5, " sec"].asSpec,
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec, 
				spread: [0, 100, 'lin', 0.0, 2, " %"].asSpec,
				azimuthWidth: [0, 360, 'lin', 0.0,  60, " deg"].asSpec,
				elevationWidth: [-180, 180, 'lin', 0.0,  0, " deg"].asSpec
				),				// specsDict
				nil, 							// default GUI
				nil,
				"Stereo input Auto 3D Vector Base Amplitude Panner",
				nil, 							// defaultAttributes
				nil,								// inRange
				nil,								// outRange
				{|plugin| 
					var speakers;
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"VBAP output not a speaker".error;
							^false;
						});
						[out.value.azi, out.value.ele];
					}, Array);
					speakers = VBAPSpeakerArray(3, speakers);
					plugin.attributes[\buffer] = 
						Buffer.loadCollection(plugin.server, speakers.getSetsAndMatrices);
				},								// setupFunc
				{|plugin|
					plugin.attributes[\buffer].free;
				}								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('3D Ambi Auto Panner', // name
				{|plugin, numInputs, numOutputs, inputs, elevation, rho, speed| // ugenGraphFunc
					var w, x, y, z;
					var atorad = (2 * pi / 360);
					var azimuth;
					
					azimuth = LFSaw.kr(speed.reciprocal).range(-180, 180);
					#w, x, y, z = BFEncode1.ar(inputs, azimuth * atorad, 
						elevation * atorad, rho);
					BFDecode1.ar(w, x, y, z, plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(speed: [0.1, 20, 'lin', 0.0,  5, " sec"].asSpec,  
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec
				),				// specsDict
				nil, 							// default GUI
				nil, // presets
				"1st Order Mono input 3D Ambisonic Auto Panner",
				nil, 							// defaultAttributes
				[1, 1],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('Stereo Auto 3D Ambi Panner', // name
				{|plugin, numInputs, numOutputs, inputs, width, elevation, rho, speed| // ugenGraphFunc
					var w, x, y, z;
					var atorad = (2 * pi / 360);
					var azimuth;
					
					azimuth = LFSaw.kr(speed.reciprocal).range(-180, 180);
					#w, x, y, z = BFEncodeSter.ar(inputs[0], inputs[1], azimuth * atorad, 
						width * atorad,
						elevation * atorad, rho);
					BFDecode1.ar(w, x, y, z, plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(speed: [0.1, 20, 'lin', 0.0,  5, " sec"].asSpec,
				width: [0, 360, 'lin', 0.0,  0, " deg"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec
				),				// specsDict
				nil, 							// default GUI
				nil, // presets
				"1st Order Stereo input Auto 3D Ambisonic Panner; inputs are L, R",
				nil, 							// defaultAttributes
				[2, 2],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
			);
			
			BMMultichannelPluginSpec('FMH Ambi Auto Panner', // name
				{|plugin, numInputs, numOutputs, inputs, elevation, rho, speed| // ugenGraphFunc
					var w, x, y, z, r, s, t, u, v;
					var atorad = (2 * pi / 360);
					var azimuth;
					
					azimuth = LFSaw.kr(speed.reciprocal).range(-180, 180);
					
					#w, x, y, z, r, s, t, u, v = FMHEncode1.ar(inputs, azimuth.neg * atorad, 
						elevation * atorad, rho);
					FMHDecode1.ar(w, x, y, z, r, s, t, u, v, 
						plugin.attributes[\speakersCoords][0], 
						plugin.attributes[\speakersCoords][1]
					);
				}, 								
				(speed: [0.1, 20, 'lin', 0.0,  5, " sec"].asSpec, 
				elevation: [-90, 90, 'lin', 0.0, 0, " deg"].asSpec,
				rho: [0, 4, 'lin', 0.0, 1].asSpec
				),				// specsDict
				nil, 							// default GUI
				('dead ahead': (azimuth: 0, elevation:0)), // presets
				"2nd Order Mono input 3D Ambisonic Auto Panner",
				nil, 							// defaultAttributes
				[1, 1],							// inRange
				[2, inf],							// outRange
				{|plugin| 
					var speakers;
					var atorad = (2 * pi / 360);
					speakers = plugin.outputs.collectAs({|out|
						out.value.isBMSpeaker.not.if({
							"Ambisonics output not a speaker".error;
							^false;
						});
						[out.value.azi * atorad, out.value.ele * atorad];
					}, Array).flop;
					plugin.attributes[\speakersCoords] = speakers;
				},								// setupFunc
				nil								// cleanupFunc
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
				sliders[key].numberView.background = Color.white.alpha_(0.4);
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
// Should this allow changing ins and outs

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
		spec = BMMultichannelPluginSpec.specs[argpluginSpecName.asSymbol];
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
		//CmdPeriod.add(this);
	}
	
	makeDef {
		defName = spec.name ++ UniqueID.next; 
		//if(attributes.notNil, { defName = defName ++ "-" ++ UniqueID.next});
		def = SynthDef(defName, {arg cfgate = 1;
			var input, out, env;
			input = In.ar(inputs);
			(input.size == 1).if({input = input[0];});
			out = SynthDef.wrap(spec.ugenGraphFunc, nil, [this, numInputs, numOutputs, input]);
			//out.postln;
			// fade in and out, release
			env = EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), cfgate, 
				doneAction: 2);
			if(out.size != numOutputs, {
				"Plugin output does not match size of output array.".warn;
			});
			// if sizes don't match take the first outputs
			// use Out not XOut for multichannel
			out.do({|chan, i| Out.ar(outputs.at(i), env * chan);});
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
		//CmdPeriod.remove(this);
	} // I'm a lame duck...
	
//	cmdPeriod { 
//		synth = nil; 
//		bus.free; 
//		CmdPeriod.remove(this);
//	}
	
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
// should addPlugin just take a symbol and populate the new method as appropriate
// - not sure we actually need ins and outs for this class, also maybe for mono version

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
	
	*new { |ins, outs, target, addAction = \addToTail, name|
		^super.new.init(ins, outs ? ins, target, addAction, name);
		// default name is class
	}
	
	init {|argins, argouts, argtarget, argaddAction, argname|
		this.initNameAndTarget(argtarget, argaddAction, argname);
		ins = argins;
		outs = argouts;
		inNames = ins.keys;
		outNames = outs.keys;
		plugins = List.new;
	}

//	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
//		^this.new(inAudioArray, group, server, name);
//	}
	
//	makeGroup { group = Group.tail(server); }
	
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
		dict = dict ? ();
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
	
	free {
		plugins.do{| plugin, i | this.removePlugin(i) };
		SystemClock.sched(BMOptions.crossfade, { group.free; group = plugins = nil });
		//CmdPeriod.remove(this)
	}
	
	// this should return an instance of our default GUI class
	// which builds the window itself
	gui { ^BMMultichannelPluginsRackGUI(this) } 
}

/// GUIs
// Quickly hacked from BMTrimPluginRackGUI and stripGUI

BMMultichannelPluginsRackGUI : BMAbstractGUI {
	var trimPluginsRack, trimPluginsStripGUIs, defaultHelpString, descriptionHelpText;
	
	*new {|trimPluginsRack, name, origin|
		^super.new.init(trimPluginsRack, name ? trimPluginsRack.name)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argtrimPluginsRack, argname|
		trimPluginsRack = argtrimPluginsRack;
		name = argname;
		trimPluginsStripGUIs = List.new;
	}
	
	makeWindow {|origin|
		var x, y, width, pluglist, numTypes, numStrips, stripGUIs, buttons;
		x = origin.x;
		y = origin.y;
		//width = 4 + 210 + 4 + min(104 * trimPluginsRack.ins.size, 1078); // max 7 visible
		width = 4 + 210 + 300;
		window = SCWindow(name, Rect.new(x, y, width, 618), false);
		window.view.decorator = FlowLayout(window.view.bounds);
		pluglist = SCScrollView(window, Rect(0, 0, 200, 508))
			.hasHorizontalScroller_(false)
			.hasBorder_(true);
		numTypes = BMMultichannelPluginSpec.specs.size;
		numStrips = 1;
		pluglist = SCVLayoutView(pluglist, Rect(4,4,190, numTypes * 24 + 4));
		BMMultichannelPluginSpec.specs.keysDo({|piName| 
			SCDragSource(pluglist, Rect(0, 0, 150, 20)).string_("   " ++ piName.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.dragLabel_(piName.asString)
				.beginDragAction_({
					piName.asSymbol;
				}) 
				.mouseDownAction_({
					descriptionHelpText.string = piName ++ ": " ++ 
						BMMultichannelPluginSpec.specs[piName].description;
				});
		});

		trimPluginsStripGUIs.add(
			BMMultichannelPluginsStripGUI(trimPluginsRack, window, trimPluginsRack.name)
		);
//		stripGUIs = SCScrollView(window, Rect(0, 0, width - 216, 508))
//			.hasVerticalScroller_(false)
//			.hasBorder_(true);
//		stripGUIs.action = {window.refresh};
//		//stripGUIs = SCHLayoutView(stripGUIs, Rect(4, 4, 104 * numStrips + 4, 500));
//		stripGUIs = SCCompositeView(stripGUIs, Rect(4, 4, 104 * numStrips + 4, 500));
//		stripGUIs.decorator = FlowLayout(stripGUIs.bounds, 0@0);
//		//trimPluginsRack.inNames.do({|chanName|
//			trimPluginsStripGUIs.add(
//				BMMultichannelPluginsStripGUI(trimPluginsRack, stripGUIs, trimPluginsRack.name)
//			);
		//});
		defaultHelpString = "Click names at left for description.\nDrag from left to add plugins.\nDouble-click or select and press enter to edit plugin settings.\nCmd down and up arrows to change order.\nCmd drag to copy trim or a plugin and its settings to another channel.";
		window.view.decorator.nextLine;
		window.view.decorator.shift(20, 0);
		
		descriptionHelpText = SCStaticText(window, Rect(0, 0, width - 58, 100))
			.string_(defaultHelpString)
			.font_(Font("Helvetica-Bold", 12));
		
		buttons = SCVLayoutView(window, Rect(0, 0, 20, 70));
		TriggerView(buttons, Rect(0, 0, 20, 20))
			.string_(" ?")
			.font_(Font("Helvetica-Bold", 14))
			.colorOn_(Color.white.alpha_(0.2))
			.action_({descriptionHelpText.string = defaultHelpString;});
//		TriggerView(buttons, Rect(0, 0, 20, 20))
//			.string_("APi")
//			.font_(Font("Helvetica-Bold", 8))
//			.colorOn_(Color.white.alpha_(0.2))
//			.action_({|v|v.value.if{trimPluginsRack.autoPlugins}});
//		TriggerView(buttons, Rect(0, 0, 20, 20))
//			.string_("ATr")
//			.font_(Font("Helvetica-Bold", 8))
//			.colorOn_(Color.white.alpha_(0.2))
//			.action_({|v| v.value.if{trimPluginsRack.autoTrim}});
//		TriggerView(buttons, Rect(0, 0, 20, 20))
//			.string_("dT")
//			.font_(Font("Helvetica-Bold", 12))
//			.colorOn_(Color.white.alpha_(0.2))
//			.action_({|v|v.value.if{trimPluginsRack.compensateDistance}});

		window.onClose = { 
			trimPluginsStripGUIs.do({|tpisg|
				tpisg.trimPluginsStrip.removeDependant(tpisg);
			});	
			onClose.value(this);
		};
		window.front;
	}
}

// only in a larger GUI
BMMultichannelPluginsStripGUI {
	var <trimPluginsStrip, containerView, ezKnob, labelView, listView;
	
	*new { |trimPluginsStrip, parent, name, origin|
		^super.new.init(trimPluginsStrip, parent).makeGUI(parent, name, origin ? 0@0);
	 }
	 
	 init {|argtrimPluginsStrip|
	 	trimPluginsStrip = argtrimPluginsStrip;
	 	trimPluginsStrip.addDependant(this);
	 }
	 
	 makeGUI{|parent, name, origin|
	 	name.postln;
	 	containerView = SCCompositeView(parent, Rect(origin.x, origin.y, 300, 508));
	 	//containerView.decorator = FlowLayout(containerView.bounds);
//	 	labelView = SCStaticText(containerView, Rect(0, 0, 300, 30))
//	 		.font_(Font("Helvetica-Bold", 13))
//	 		.background_(Color.grey.alpha_(0.3))
//	 		.string_(" " ++ name);
//	 	ezKnob = EZKnob(containerView, 50@20, " Trim (dBFS)", \db.asSpec, 
//	 		{|ez| trimPluginsStrip.trim_(ez.value);}, trimPluginsStrip.trim, false, 96, 70);
//	 	ezKnob.labelView.align_(\left).font_(Font("Helvetica-Bold", 12));
//	 	ezKnob.numberView.background_(Color.white.alpha_(0.3));
	 	listView = SCListView(containerView, Rect(0, 0, 300, 508))
	 		.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
	 	listView.enterKeyAction = {
	 		var plgin;
	 		plgin = trimPluginsStrip.plugins[listView.value];
	 		plgin.notNil.if({plgin.gui}); 
	 	}; // can duplicate
	 	listView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
	 		block { |break|
				if((modifiers == 11534600) && (unicode == 63233), {
					trimPluginsStrip.movePluginDown(listView.value);
					break.value;
				});
				if((modifiers == 11534600) && (unicode == 63232), {
					trimPluginsStrip.movePluginUp(listView.value);
					break.value;
				});
				if(unicode == 127, {trimPluginsStrip.removePlugin(listView.value)});
				listView.defaultKeyDownAction(char,modifiers,unicode);
			}
		};
		listView.mouseDownAction = {|view, x, y, modifiers, buttonNumber, clickCount|
			if(clickCount == 2, {
				listView.enterKeyAction.value;
			});
		};
		listView.canReceiveDragHandler = { SCView.currentDrag.isKindOf(Symbol); };
		listView.receiveDragHandler = {
			var piName;
			piName = SCView.currentDrag;
			BMSelectInsOutsGUI(parent, trimPluginsStrip.ins, trimPluginsStrip.outs, {|ins, outs|
				var plugin;
				ins.postln;
				outs.postln;
				plugin = BMMultichannelPlugin(piName, ins, outs, 
					trimPluginsStrip.server);
				// protect against bad plugin inputs
				plugin.notNil.if({trimPluginsStrip.addPlugin(plugin)});
			});
		};
		listView.beginDragAction = { trimPluginsStrip.plugins[listView.value].copy };
	 }
	 
	 update {|tpv, what|
	 	//if(what == \trim, {ezKnob.value = trimPluginsStrip.trim;});
	 	listView.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
	 	switch(what,
	 		\moveDown, {listView.value = listView.value + 1},
	 		\moveUp, {listView.value = listView.value - 1}
	 	)
	 }

}

BMSelectInsOutsGUI : BMAbstractGUI {
	var ins, outs, okFunc;
	
	*new {|parent, ins, outs, okFunc|
		^super.new.init(ins, outs, okFunc)
			.makeWindow(parent);
	}
	
	init {|argins, argouts, argokfunc|
		ins = argins;
		outs = argouts;
		okFunc = argokfunc;
		name = "Define Ins and Outs";
	}
	
	makeWindow {|parent|
		var buttons, insSources, insLV, outsSources, insSubArrays, outsSubArrays;
		var inResult, outResult, outsLV, dragSource;
		window = SCModalSheet(parent, Rect.new(0, 0, 500, 620), false);
		window.view.decorator = FlowLayout(window.view.bounds);
		
		// ins
		SCStaticText(window, Rect(0, 0, 100, 30))
			.string_("Inputs")
			.font_(Font("Helvetica-Bold", 14));
		window.view.decorator.nextLine;
		insSources = SCScrollView(window, Rect(0, 0, 160, 254))
			.hasHorizontalScroller_(false)
			.hasBorder_(true);
		insLV = SCVLayoutView(insSources, Rect(4,4,150, ins.size * 24 + 4));
		ins.keys.do({|inKey| 
			SCDragSource(insLV, Rect(0, 0, 150, 20)).string_("   " ++ inKey.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.dragLabel_(inKey.asString)
				.beginDragAction_({
					dragSource = \ins;
					inKey
				}); 
//				.mouseDownAction_({
//					descriptionHelpText.string = piName ++ ": " ++ 
//						BMMultichannelPluginSpec.specs[piName].description;
//				});
		});
		
		
		inResult = SCListView(window, Rect(0, 0, 160, 254)).font_(Font("Helvetica-Bold", 12));
		inResult.canReceiveDragHandler = { 
			dragSource == \ins;
		};
		inResult.receiveDragHandler = { 
			dragSource = nil;
			inResult.items = inResult.items.add(SCView.currentDrag)
		};
		inResult.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			var newItems;
			//\foo.postln;
	 		block { |break|
				if((modifiers == 11534600) && (unicode == 63233), {
					if(view.value < (view.items.size -1), {
						view.items = view.items.postln.swap(view.value, view.value + 1).postln;
						view.refresh;
						view.value = view.value + 1;
					});
					break.value;
				});
				if((modifiers == 11534600) && (unicode == 63232), {
					if(view.value > 0, {
						view.items = view.items.swap(view.value, view.value - 1);
						//view.value = view.value - 1;
					});
					break.value;
				});
				if(unicode == 127, {
					view.item.notNil.if({
						newItems = view.items;
						newItems.removeAt(view.value);
						view.items = newItems;
					});
					break.value;
				});
				view.defaultKeyDownAction(char,modifiers,unicode);
			}
		};
		insSubArrays = SCPopUpMenu(window, Rect(0, 0, 160, 20))
			.font_(Font("Helvetica-Bold", 12))
			.items_(["Add subarray", "-"] ++ ins.subArrays)
			.action_({|menu|
				var subArray;
				subArray = ins.getSubArray(menu.item.asSymbol);
				subArray.notNil.if({inResult.items = inResult.items ++ subArray.keys});
				menu.value = 0;
			});
		
		// outs
		SCStaticText(window, Rect(0, 0, 100, 30))
			.string_("Outputs")
			.font_(Font("Helvetica-Bold", 14));
		window.view.decorator.nextLine;
		outsSources = SCScrollView(window, Rect(0, 0, 160, 254))
			.hasHorizontalScroller_(false)
			.hasBorder_(true);
		outsLV = SCVLayoutView(outsSources, Rect(4,4,150, outs.size * 24 + 4));
		outs.keys.do({|outKey| 
			SCDragSource(outsLV, Rect(0, 0, 150, 20)).string_("   " ++ outKey.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.dragLabel_(outKey.asString)
				.beginDragAction_({
					dragSource = \outs;
					outKey
				}); 
//				.mouseDownAction_({
//					descriptionHelpText.string = piName ++ ": " ++ 
//						BMMultichannelPluginSpec.specs[piName].description;
//				});
		});
		outResult = SCListView(window, Rect(0, 0, 160, 254)).font_(Font("Helvetica-Bold", 12));
		outResult.canReceiveDragHandler = { 
			dragSource == \outs;
		};
		outResult.receiveDragHandler = { 
			dragSource = nil;
			outResult.items = outResult.items.add(SCView.currentDrag)
		};
		outResult.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			var newItems;
			//\foo.postln;
	 		block { |break|
				if((modifiers == 11534600) && (unicode == 63233), {
					if(view.value < (view.items.size -1), {
						view.items = view.items.postln.swap(view.value, view.value + 1).postln;
						view.refresh;
						view.value = view.value + 1;
					});
					break.value;
				});
				if((modifiers == 11534600) && (unicode == 63232), {
					if(view.value > 0, {
						view.items = view.items.swap(view.value, view.value - 1);
						//view.value = view.value - 1;
					});
					break.value;
				});
				if(unicode == 127, {
					view.item.notNil.if({
						newItems = view.items;
						newItems.removeAt(view.value);
						view.items = newItems;
					});
					break.value;
				});
				view.defaultKeyDownAction(char,modifiers,unicode);
			}
		};
		outsSubArrays = SCPopUpMenu(window, Rect(0, 0, 160, 20))
			.font_(Font("Helvetica-Bold", 12))
			.items_(["Add subarray", "-"] ++ ins.subArrays)
			.action_({|menu|
				var subArray;
				subArray = outs.getSubArray(menu.item.asSymbol);
				subArray.notNil.if({outResult.items = outResult.items ++ subArray.keys});
				menu.value = 0;
			});

		window.view.decorator.nextLine;
		window.view.decorator.shift(window.bounds.width - 242, 0);
		
		RoundButton(window, 115 @ 20)
			.extrude_(false).canFocus_(false) 
			.states_([[ "Cancel", Color.black, Color.white.alpha_(0.8) ]])
			.action_({ window.close });
			   
		RoundButton(window, 115 @ 20)
			.extrude_(false).canFocus_(false)
			.states_([[ "OK", Color.black, Color.new255(51, 111, 203, 255 * 0.95) ]])
			.action_({ 
				window.close;
				okFunc.value(
					inResult.items.collectAs({|key| key->ins[key]}, BMInOutArray),
					outResult.items.collectAs({|key| key->outs[key]}, BMInOutArray)
				);
				onClose.value(this);
			});
		
	}
	
}