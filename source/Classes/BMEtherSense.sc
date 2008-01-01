// valueArray holds the controller value in its native form
// setFaderValue should convert to 0-1 and send to the bus 
BMEtherSense : BMAbstractController {
	//classvar <allControllers;
	//var <name, <bus, <busIndex, valueArray, labelArray, <server, <numFaders;
	var spec, addr, responders, numDaughterBoards;
	
	// address should be with port 57120 (sclang)
	*new { |addr, name, server, numDaughterBoards = 2|
		^super.new.init(addr, name, server ? Server.default, numDaughterBoards);
	}
	
	init { |argaddr, argname, argserver, argnumDaughterBoards|
		addr = argaddr;
		name = argname;
		server = argserver;
		numDaughterBoards = argnumDaughterBoards;
		responders = Array.newClear(numDaughterBoards);
		numFaders = 32;
		valueArray = Array.fill(numFaders, {0});
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		spec = Env([0, 1], [65536], \sine);
		this.startListening;
		//this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	startListening { 
		
		responders[0] = OSCresponderNode(addr, '/Ethersense01/Card01', { arg time, resp, msg; 			msg.copyToEnd(1).do({|item, i| this.updateValue(i, item);});
		}).add;
		responders[1] = OSCresponderNode(addr, '/Ethersense01/Card02', { arg time, resp, msg; 			msg.copyToEnd(1).do({|item, i| this.updateValue(i + 16, item);});
		}).add;
	}
	
	stopListening { responders.do(_.remove); }
	
	updateValue { |chan, bend|
		var value;
		value = spec.at(bend);
		server.sendMsg("/c_set", busIndex + chan, value);
		valueArray[chan] = bend;
	}
		
	updateAllFaders { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}
	
	// assumes fader 1 = 1 not 0
	// returns 16 bit value
	getFaderVal { |faderNum| ^valueArray[faderNum -1] }
	
	setFaderVal { |faderNum, val| this.updateValue(faderNum -1, val) }
	
	getAllFaders { ^valueArray }
	
	setAllFaders {|array| { array.do({|item, i| this.updateValue(i, item); 0.1.wait; }); }.fork;}
	
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
