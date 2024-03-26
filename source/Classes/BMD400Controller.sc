// This is essentially a cluster controller for a multi port MIDI device
// for network use, we need to do live routings to/from on remote computer, and select the network port on this computer
// One network MIDI session for each controller

BMD400Controller : BMAbstractController {
	var <ports, numPorts, midiFuncs, midiOuts;
	var <>loopBack = false;
	var <>acceptsAutomation = false;
	var inputSpec;

	*new { |name, numControls = 32, server| // name must correspond to the MIDI device name
		^super.new.init(name, numControls, server ? Server.default).acceptsAutomation_(true).zeroControls;
	}

	init { |argName, argNumControls, argServer|
		name = argName;
		numControls = argNumControls;
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
	}

	*newFromParamDict {|dict, server|
		^this.new(dict[\midiport], dict[\name], server);
	}

	*parameterList {
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			numControls: [Integer, [1, 64, \linear, 1].asSpec, "Number of Faders"]
		);
	}

	*humanName {  ^"Asparion D400"  }

	startListening {
		midiFuncs = numPorts.collect({|i|
			MIDIFunc.bend({|val, chan|
				this.updateValue((i*8) + chan, val);
			}, (0..7), ports[i].inuid).fix
		});
	}

	stopListening {
		midiFuncs.do({|func| func.free });
		midiFuncs = nil;
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

	// do later
	setLabel { |controlNum, name|  }

	getLabel { |controlNum| ^nil }

	getAllLabels { ^nil }

	setAllLabels { |array| }

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