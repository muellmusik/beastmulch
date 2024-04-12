// This is essentially a cluster controller for a multi port MIDI device
// As such it's not a subclass of BMMIDIController
// for network use, we need to do live routings to/from on remote computer, and select the network port on this computer
// One network MIDI session for each controller

BMD400Controller : BMAbstractController {
	classvar labelHeader = #[0xF0, 0x00, 0x00, 0x66, 0x10, 0x12];
	var <ports, numPorts, midiFuncs, midiOuts, transportMIDIFunc;
	var <>loopBack = false;
	var <>acceptsAutomation = false;
	var inputSpec;

	*new { |name, numControls = 32, server| // name must correspond to the MIDI device name
		^super.new.init(name, numControls, server ? Server.default).acceptsAutomation_(true).zeroControls;
	}

	init { |argName, argNumControls, argServer|
		name = argName;
		numControls = argNumControls.asInteger;
		server = argServer;
		if(numControls%8 != 0, {"D400 number of controls should be a multiple of 8".warn});
		if(BMMIDIPort.ports.collectAs({|port| port.device}, Set).includes(name.asString).not, {"D400 name must match existing MIDI Device name. Initialisation failed".warn});
		numPorts = (numControls / 8).asInteger;
		numPorts.do({|i| ports = ports.add(BMMIDIPort.ports[("Port" + (i + 1)).asSymbol])});
		valueArray = Array.fill(numControls, {0});
		bus = Bus.control(server, numControls);
		busIndex = bus.index;
		this.startListening;
		midiOuts = ports.collect({|port| MIDIOut(port.outport, port.outuid) });
		this.makeInputSpec;
		this.addControlsToIndex;
		this.updateAllValues(valueArray.copy);
		allControllers[name] = this;
		labelArray = Array.fill(numControls, {""});
		this.setAllLabels(numControls.collect({arg i; (i+1).asString}));
	}

	*newFromParamDict {|dict, server|
		^this.new(dict[\name], dict[\numControls], server);
	}

	*parameterList {
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			numControls: [Integer, [8, 64, \linear, 32].asSpec, "Number of Faders"]
		);
	}

	*humanName {  ^"Asparion D400"  }

	startListening {
		midiFuncs = numPorts.collect({|i|
			MIDIFunc.bend({|val, chan|
				this.updateValue((i*8) + chan, val);
			}, (0..7), ports[i].inuid).fix
		});
		transportMIDIFunc = MIDIFunc.noteOn({|vel, num|
			if(transportTarget.notNil, {
				switch(num,
					94, { transportTarget.togglePlay }, // play
					93, { transportTarget.stop }, // stop
				)
			})
		}, srcID: ports[0].inuid)
	}

	update {|changer, changed|
		switch(changed,
			\play, { midiOuts[0].noteOn(0, 94, 127) },
			\pause, { midiOuts[0].noteOn(0, 94, 0) },
			\stop, { midiOuts[0].noteOn(0, 94, 0) }
		)
	}

	stopListening {
		midiFuncs.do({|func| func.free });
		midiFuncs = nil;
		transportMIDIFunc.free;
		transportMIDIFunc = nil;
	}

	zeroControls { this.setAllValues(0 ! numControls) }

	makeInputSpec {
		inputSpec = [0, 16383].asSpec;
	}

	loopback {|ind, val|
		midiOuts[(ind/8).asInteger].bend(ind%8, val);
	}

		// val is native midi value
	updateValue { |ind, val|
		var value;
		valueArray[ind] = val;
		value = controls[ind].controlSpec.map(inputSpec.unmap(val)); // convert from midi to 0..1 and then add curve
		server.sendMsg("/c_set", busIndex + ind, value);
		if(loopBack || acceptsAutomation, {this.loopback(ind, val)});
	}

	updateAllValues { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}

	// assumes fader 1 = 1 not 0
	// returns value between 0 and 1
	getVal { |controlNum|
		^controls[controlNum -1].controlSpec.map(inputSpec.unmap(valueArray[controlNum -1]))
	}

	setVal { |controlNum, val|
		this.updateValue(controlNum -1,
			inputSpec.map(controls[controlNum-1].controlSpec.unmap(val)))
	}

	getAllValues {
		^valueArray.collect({|val, i| controls[i].controlSpec.map(inputSpec.unmap(val))})
	}


	setAllValues {|array|
		array.do({|item, i|
			this.updateValue(i, inputSpec.map(controls[i].controlSpec.unmap(item)))
		});
	}

	setLabel { |fader, name|
		var firstSpace, splitBySpace, msgUpper, msgLower, midiOut, offset;
		// need to split the string sensibly to go on upper and lower lines
		// (by space if there is one and halves will fit, otherwise just wrap)
		// then zeropad to overwrite, and send
		firstSpace = name.find(" ") ?? inf;
		splitBySpace = (firstSpace <= 7) && (name.size - firstSpace <=7);
		#msgUpper, msgLower = if(splitBySpace, {name.split(Char.space)}, {[name.copyFromStart(6), name.copyRange(7,13)]});
		offset = (fader - 1)%8 * 7; // calculate character offset for this fader on the correct subdevice
		midiOut = midiOuts[((fader - 1)/8).asInteger]; // get the MIDIOut of the D400F subunit
		midiOut.sysex((labelHeader ++ offset ++ msgUpper.padRight(7, " ").ascii ++ 0xF7).as(Int8Array)); // set the upper chars
		midiOut.sysex((labelHeader ++ (0x38 + offset) ++ msgLower.padRight(7, " ").ascii ++ 0xF7).as(Int8Array)); // set the upper chars
		labelArray[fader - 1] = name;
		this.changed(\label, fader - 1, name);
	}

	getLabel { |fader| ^labelArray[fader-1] }

	getAllLabels {  ^labelArray  }

	setAllLabels {|array| array.do({|item, i| this.setLabel(i+1, item);}); }

	hasLabels { ^true }

/*	setVal { |chan, val|
		midiout.bend(chan-1, val);
		this.updateValue(chan -1, inputSpec.map(controls[chan-1].controlSpec.unmap(val)))
	}

	setAllValues {|array|
		array.do({|item, i|
			this.setVal(i + 1, item);
			///this.updateValue(i, inputSpec.map(controls[i].controlSpec.unmap(item)))
		});
	} */

/*	updateValue { |ind, val|
		var value;
		valueArray[ind] = val;
		value = controls[ind].controlSpec.map(inputSpec.unmap(val)); // convert from midi to 0..1 and then add curve
		server.sendMsg("/c_set", busIndex + ind, value);
		// don't loopback
		if(loopBack || acceptsAutomation, {\updating.postln; this.loopback(ind, val.postln)});
	}*/

	mappings {
		^IdentityDictionary[\faderVals->this.getAllValues];
	}

	mappings_ {|mappings|
		mappings = mappings ? ();
		this.setAllValues(mappings[\faderVals]);
	}

}


// BMD400Controller : BMMIDIBendController {
//
// 	*new { |midiport, name, server|
// 		^super.new(midiport, name, server).acceptsAutomation_(true).zeroControls;
// 	}
//
// 	*newFromParamDict {|dict, server|
// 		^this.new(dict[\midiport], dict[\name], server);
// 	}
//
// 	/*	*parameterList {
// 	var class;
// 	class = this;
// 	^(
// 	name: [Symbol, {class.makeName}, "Name"],
// 	midiport: [BMMIDIPort, nil, "MIDI Port"]
// 	);
// 	}*/
//
// 	*humanName {  ^"Asparion D400"  }
//
// 	zeroControls { this.setAllValues(0 ! numControls) }
//
// 	setNumControls { numControls = 8;}
//
// 	/*	setVal { |chan, val|
// 	midiout.bend(chan-1, val);
// 	this.updateValue(chan -1, inputSpec.map(controls[chan-1].controlSpec.unmap(val)))
// 	}
//
// 	setAllValues {|array|
// 	array.do({|item, i|
// 	this.setVal(i + 1, item);
// 	///this.updateValue(i, inputSpec.map(controls[i].controlSpec.unmap(item)))
// 	});
// 	} */
//
// 	/*	updateValue { |ind, val|
// 	var value;
// 	valueArray[ind] = val;
// 	value = controls[ind].controlSpec.map(inputSpec.unmap(val)); // convert from midi to 0..1 and then add curve
// 	server.sendMsg("/c_set", busIndex + ind, value);
// 	// don't loopback
// 	if(loopBack || acceptsAutomation, {\updating.postln; this.loopback(ind, val.postln)});
// 	}*/
//
// 	mappings {
// 		^IdentityDictionary[\faderVals->this.getAllValues];
// 	}
//
// 	mappings_ {|mappings|
// 		mappings = mappings ? ();
// 		this.setAllValues(mappings[\faderVals]);
// 	}
//
// }

/*
BMMIDIPort.init
~d400 = BMD400Controller("D 400");
~d400 = BMD400Controller(BMMIDIPort.ports['Test D400 in Studio 1'], 'D400');


//~d400 = BMMIDIBendController(BMMIDIPort.ports['Port 1'], 'D400');

~d400.getAllValues

~map = ~d400.mappings

~d400.mappings_(~map)
~d400.setAllValues(1!32)

~d400.setVal(23, 1)

~d400.getVal(23)

~d400.updateValue(1, 12000)
~d400.acceptsAutomation = true

~d400.controls[0].controlSpec.map(ControlSpec(0, 16384).unmap(12000))

MIDIFunc.trace

MIDIFunc.bend({arg ...msg; msg.postln})

MIDIIn.bend_({arg ...msg; msg.postln})

~d400.midiout.bend(0, 16000)
*/