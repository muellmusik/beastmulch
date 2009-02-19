// uses a control bus on the server to map values
// bend goes from 0 to 16384 and is mapped to values between 0 and 1 (assumes amplitude for the moment)
// assumes MIDIClient is initialised

BMAbstractMIDIController : BMAbstractController {
	var <uid, <outPort, <outUid, <midiout;
	var responder, <>loopBack = false;
	var <>acceptsAutomation = false;
	
//	*new { |uid, name, server|
//		^super.new.init(uid, name, server ? Server.default).addControlsToIndex;
//	}
	
	startListening { 
		this.subclassResponsibility(thisMethod);
	}
	
	makeSpec { 
		this.subclassResponsibility(thisMethod);
	}
	
	init { |arguid, argname, argserver|
		var titleArray, nameString;
		uid = arguid;
		name = argname;
		server = argserver.postln;
		("Server: " ++ server).postln;
		this.setNumFaders;
		valueArray = Array.fill(numFaders, {0});
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		this.setOutUid.startListening;
		midiout = MIDIOut(outPort, outUid);
		this.makeSpec;
		this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	setNumFaders {
		this.subclassResponsibility(thisMethod);
	}
	
	loopback {
		this.subclassResponsibility(thisMethod);
	}

	setOutUid {
		MIDIClient.sources.do({ |source, i| 
			if(source.uid == uid, { 
				outUid = MIDIClient.destinations[i].uid;
				outPort = i;
			});	
		});
		outUid.isNil.if({("destination for" + uid + "not found.").warn});
	}
	
	updateValue { |ind, val|
		var value;
		// map for amplitude
		//value = spec.unmap(bend.post + 1); // exp warp so can't have zero
		//" ".post;
		value = spec.unmap(val);
		server.sendMsg("/c_set", busIndex + ind, value);
		valueArray[ind] = val;
		if(loopBack || acceptsAutomation, {this.loopback(ind, value)});
	}
		
	updateAllFaders { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}
	
	// assumes fader 1 = 1 not 0
	// returns 14 bit value
	getFaderVal { |faderNum| ^valueArray[faderNum -1] }
	
	setFaderVal { |faderNum, val| this.updateValue(faderNum -1, val) }
	
	getAllFaders { ^valueArray }
	
	setAllFaders {|array| array.do({|item, i| this.updateValue(i, item); });}
	
	
	setAllLabels { |array| array.do({|item, i| this.setLabel(i+1, item); });}

}

// 14 bit bend
BMMIDIBendController : BMAbstractMIDIController {

	*new { |uid, name, server|
		^super.new.init(uid, name, server ? Server.default).addControlsToIndex;
	}

	setNumFaders { numFaders = 16;}
	
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

	*new { |uid, name, server, chan, ccArray|
		^super.new
			.setCCParams(chan, ccArray)
			.init(uid, name, server ? Server.default).addControlsToIndex;
	}
	
	setNumFaders { numFaders = ccArray.size}
	
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

