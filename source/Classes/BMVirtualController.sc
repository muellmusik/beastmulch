// a simple controller that allows for onscreen guis.
// also allows for making user-level wrappers for arbitrary devices without subclassing

BMVirtualController : BMAbstractController {

	*new { |name, server, numControls = 8|
		^super.new.init(name.asSymbol, server ? Server.default, numControls).addControlsToIndex;
	}
	
	*newFromParamDict {|dict, server| 
		^this.new(dict[\name], server, dict[\numControls]);
	}
	
	*parameterList { 
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			numControls: [Integer, [1, 64, \linear, 1].asSpec, "Number of Faders"]
		); 
	}
	
	*humanName {  ^"GUI Controller"  }
	
	init { |argname, argserver, argnumControls|
		name = argname;
		server = argserver;
		numControls = argnumControls;

		// possibly should move this into super
		valueArray = Array.fill(numControls, {0});
		labelArray = Array.fill(numControls, {""});
		bus = Bus.control(server, numControls);
		busIndex = bus.index;
		allControllers[name] = this;
	}
	
	getVal { |controlNum|
		^valueArray[controlNum -1];
	}
	
	// there's no need to do any mapping here, but any GUI sliders etc. controlling this should use the control's spec
	setVal { |controlNum, val| 
		var chan;
		chan = controlNum - 1;
		server.sendMsg("/c_set", busIndex + chan, val);
		valueArray[chan] = val; 
		this.changed(\controlVal, chan, val);
	}

	getAllValues { ^valueArray.copy; }

	setAllValues {|array|  array.do({|item, i| this.setVal(i + 1, item);}); }
	
	setLabel { |fader, name|
		labelArray[fader - 1] = name;
		this.changed(\label, fader - 1, name);
	}
	
	getLabel { |fader| ^labelArray[fader-1] }
	
	getAllLabels {  ^labelArray  }
	
	setAllLabels {|array| array.do({|item, i| this.setLabel(i+1, item);}); }

	acceptsAutomation { ^true }
}
