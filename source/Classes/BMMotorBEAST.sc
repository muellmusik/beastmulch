
// valueArray holds the controller value in its native form
// setFaderValue should convert to 0-1 and send to the bus 
BMMotorBEAST : BMAbstractController {
	//classvar <allControllers;
	//var <name, <bus, <busIndex, valueArray, labelArray, <server, <numFaders;
	var spec, <addr, responder;
	
	// address should be with port 57120 (sclang)
	*new { |addr, name, server|
		^super.new.init(addr, name, server ? Server.default).addControlsToIndex;
	}
	
	init { |argaddr, argname, argserver|
		addr = argaddr;
		name = argname;
		server = argserver;
		numFaders = 32;
		valueArray = 0 ! 32;
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		spec = Env([0, 1], [65536], \sine);
		this.startListening;
		//this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	startListening { 
		// do updates directly here to minimize dispatch
		responder = OSCresponderNode(addr, '/analogMF', { arg time, resp, msg; 
			var values;
			values = msg.copyToEnd(1);
			server.sendMsg("/c_setn", busIndex, 16, *(values.collect({|val| spec.at(val)})));
			valueArray= values;
			this.changed(\faderVal);
		}).add;
	}
	
	stopListening { responder.remove; responder = nil }
	
	// assumes fader 1 = 1 not 0
	// returns 16 bit value
	getFaderVal { |faderNum| ^valueArray[faderNum -1] }
	
	// we set the local value on loopback, so we're always in sync
	setFaderVal { |faderNum, val| addr.sendMsg("/MF/" ++ faderNum, val) }
	
	getAllFaders { ^valueArray }
	
	// 32 faders
	setAllFaders {|array|
		addr.sendMsg("/MF", *array)
	}
	
	// for faders
//	getInputArray {
//		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}
	
//	faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}
	
	// perhaps this should be more generalised and named something else like 'preset'
	mappings {
		^IdentityDictionary[\faders->this.getAllFaders];
	}
	
	mappings_ {|mappings|
		this.setAllFaders(mappings[\faders]);
	}
	
	// this has no labels
	setLabel { |fader, name| this.shouldNotImplement(thisMethod) }
	
	getLabel { |fader| ^this.shouldNotImplement(thisMethod) }
	
	getAllLabels { ^this.shouldNotImplement(thisMethod) }
	
	setAllLabels { |array| this.shouldNotImplement(thisMethod)}
	
	initFromArchive { this.startListening }
	
	asTextArchive { 
		var arch;
		this.stopListening; 
		arch = super.asTextArchive;
		this.startListening;
		^arch	
	}

}
