// uses a control bus on the server to map values
// bend goes from 0 to 16384 and is mapped to values between 0 and 1 (assumes amplitude for the moment)
// assumes MIDIClient is initialised

BMAbstractMIDIController : BMAbstractController {
	var <midiport, <uid, <outPort, <outUid, <midiout;
	var responder, <>loopBack = false;
	var <>acceptsAutomation = false;
	
//	*new { |midiport, name, server|
//		^super.new.init(uid, name, server ? Server.default).addControlsToIndex;
//	}
	
	startListening { 
		this.subclassResponsibility(thisMethod);
	}
	
	makeSpec { 
		this.subclassResponsibility(thisMethod);
	}
	
	init { |argmidiport, argname, argserver|
		midiport = argmidiport;
		uid = midiport.inuid;
		outUid = midiport.outuid;
		outPort = midiport.outport;
		outUid.isNil.if({("outport for" + name + "not found.").warn});
		name = argname;
		server = argserver;
		this.setNumControls;
		valueArray = Array.fill(numControls, {0});
		bus = Bus.control(server, numControls);
		busIndex = bus.index;
		this.startListening;
		midiout = MIDIOut(outPort, outUid);
		this.makeSpec;
		this.updateAllValues(valueArray);
		allControllers[name] = this;
	}
	
	setNumControls {
		this.subclassResponsibility(thisMethod);
	}
	
	loopback {
		this.subclassResponsibility(thisMethod);
	}
	
	updateValue { |ind, val|
		var value;
		//server.sendMsg("/c_set", busIndex + ind, val);
		//valueArray[ind] = value = spec.map(val).asInteger;
		valueArray[ind] = value = spec.unmap(val).asInteger;
		server.sendMsg("/c_set", busIndex + ind, value);
		if(loopBack || acceptsAutomation, {this.loopback(ind, val)});
	}
		
	updateAllValues { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}
	
	// assumes fader 1 = 1 not 0
	// returns value between 0 and 1
	getVal { |controlNum| ^spec.map(valueArray[controlNum -1]) }
	
	setVal { |controlNum, val| this.updateValue(controlNum -1, spec.map(val)) }
	
	getAllValues { ^valueArray.collect({|val| spec.unmap(val)}) }
	
	setAllValues {|array| array.do({|item, i| this.updateValue(i, item); });}
	
	// this has no labels
	setLabel { |controlNum, name| this.shouldNotImplement(thisMethod) }
	
	getLabel { |controlNum| ^this.shouldNotImplement(thisMethod) }
	
	getAllLabels { ^this.shouldNotImplement(thisMethod) }
	
	setAllLabels { |array| this.shouldNotImplement(thisMethod)}

}

// 14 bit bend
BMMIDIBendController : BMAbstractMIDIController {

	*new { |midiport, name, server|
		^super.new.init(midiport, name, server ? Server.default).addControlsToIndex;
	}
	
	*newFromParamDict {|dict, server| 
		^this.new(dict[\midiport], dict[\name], server);
	}
	
	*parameterList { 
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			midiport: [BMMIDIPort, nil, "MIDI Port"]
		); 
	}
	
	*humanName {  ^"MIDI Pitchbend Controller"  }

	setNumControls { numControls = 16;}
	
	makeSpec {
		spec = [0, 16384, 'cos', 0.0].asSpec;
	}
	
	startListening { 
//		faderRoutine = Routine({
//			var	event, port, channel, bend;
//			loop {
//				event = MIDIIn.waitBend(uid);
//				this.updateValue(event.chan, event.b);
//			}
//		}).play;
		responder = BendResponder({|src, chan, value|
			this.updateValue(chan, value);
		}, uid);
	}
	
	loopback {|ind, val|
		midiout.bend(ind, val);
	}

}

// MidiControllers on a particular channel
BMMIDICCController : BMAbstractMIDIController {
	var chan, ccArray;

	*new { |midiport, name, chan, ccArray, server|
		^super.new
			.setCCParams(chan, ccArray)
			.init(midiport, name, server ? Server.default).addControlsToIndex;
	}
	
	*newFromParamDict {|dict, server| 
		^this.new(dict[\midiport], dict[\name], dict[\chan], dict[\ccArray], server);
	}
	
	*parameterList { 
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			midiport: [BMMIDIPort, nil, "MIDI Port"],
			chan: [Integer, [0, 15, \linear, 1, 0].asSpec, "MIDI Channel"],
			ccArray: [Int8Array, "", "CC numbers"]
		); 
	}
	
	*humanName {  ^"MIDI CC Controller"  }
	
	setNumControls { numControls = ccArray.size}
	
	setCCParams { |argchan, argccArray|
		chan = argchan;
		ccArray = argccArray;
	}

	makeSpec {
		spec = [0, 127, 'cos', 0.0].asSpec;
	}
	
	startListening { 
		responder = CCResponder({|src, chan, num, value|
			this.updateValue(ccArray.indexOf(num), value);
		}, uid, chan, ccArray);
	}
	
	loopback {|ind, val|
		midiout.control(chan, ccArray[ind], val);
	}

}

