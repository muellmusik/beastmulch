// valueArray holds the controller value in its native form
// setFaderValue should convert to 0-1 and send to the bus 
BMEtherSense : BMAbstractController {
	//classvar <allControllers;
	//var <name, <bus, <busIndex, valueArray, labelArray, <server, <numFaders;
	var spec, addr, responders, busBoard2Index;
	
	// address should be with port 57120 (sclang)
	*new { |addr, name, server|
		^super.new.init(addr, name, server ? Server.default);
	}
	
	init { |argaddr, argname, argserver|
		addr = argaddr;
		name = argname;
		server = argserver;
		responders = Array.newClear(2);
		numFaders = 32;
		valueArray = Array.fill(2, {0 ! 16});
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		busBoard2Index = busIndex + 16; // save an add every message
		spec = Env([0, 1], [65536], \sine);
		this.startListening;
		//this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	startListening { 
		// do updates directly here to minimize dispatch
		responders[0] = OSCresponderNode(addr, '/Ethersense01/Card01', { arg time, resp, msg; 
			var values;
			values = msg.copyToEnd(1);
			server.sendMsg("/c_setn", busIndex, 16, *(values.collect({|val| spec.at(val)})));
			valueArray[0] = values;
		}).add;
		responders[1] = OSCresponderNode(addr, '/Ethersense01/Card02', { arg time, resp, msg; 			var values;
			values = msg.copyToEnd(1);
			server.sendMsg("/c_setn", busBoard2Index, 16, *(values.collect({|val| spec.at(val)})));
			valueArray[1] = values;
		}).add;
	}
	
	stopListening { responders.do(_.remove); }
	
	// assumes fader 1 = 1 not 0
	// returns 16 bit value
	getFaderVal { |faderNum| ^valueArray[faderNum -1] }
	
	setFaderVal { |faderNum, val| this.updateValue(faderNum -1, val) }
	
	getAllFaders { ^valueArray.flat }
	
	// 32 faders
	setAllFaders {|array|
		server.sendMsg("/c_setn", busIndex, 32, spec.at(array));
		valueArray = array.reshape(2, 16);
	}
	
	// for faders
	getInputArray {
		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, InOutArray);
	}
	
	faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}
	
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
}
