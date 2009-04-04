// a simple controller that allows for onscreen guis.
// also allows for making user-level wrappers for arbitrary devices without subclassing
// 'native' values for this are 0-1

BMVirtualController : BMAbstractController {

	*new { |name, server, numFaders = 8|
		^super.new.init(name, server ? Server.default, numFaders).addControlsToIndex;
	}
	
	*newFromParamDict {|dict, server| 
		^this.new(dict[\name], dict[\numFaders], server);
	}
	
	*parameterList { 
		^(
			name: [String, nil, "Name"],
			numFaders: [Integer, [1, 64, \linear, 1].asSpec, "Number of Faders"]
		); 
	}
	
	*humanName {  ^"GUI Faders"  }
	
	init { |argname, argserver, argnumFaders|
		name = argname;
		server = argserver.postln;
		numFaders = argnumFaders;

		// possibly should move this into super
		valueArray = Array.fill(numFaders, {0});
		labelArray = Array.fill(numFaders, {""});
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		allControllers[name] = this;
	}
	
	getFaderVal { |faderNum|
		^valueArray[faderNum -1];
	}
	
	setFaderVal { |faderNum, val| 
		var chan;
		chan = faderNum - 1;
		server.sendMsg("/c_set", busIndex + chan, val);
		valueArray[chan] = val; 
		this.changed(\faderVal, chan, val);
	}
	
	getAllFaders { ^valueArray; }
	
	setAllFaders {|array|  array.do({|item, i| this.setFaderVal(i + 1, item);}); }
	
	setLabel { |fader, name|
		labelArray[fader - 1] = name;
		this.changed(\label, fader - 1, name);
	}
	
	getLabel { |fader| ^labelArray[fader-1] }
	
	getAllLabels {  ^labelArray  }
	
	setAllLabels {|array| array.do({|item, i| this.setLabel(i+1, item);}); }
	
//	getInputArray {
//		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}
	
//	faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}

	acceptsAutomation { ^true }
}

// simple onscreen slider GUI for a BMVirtualController
BMVirtualControllerSliders : BMAbstractGUI {
	var virtualCont, sliders, fromUpdate = false;
	var needsRefresh = false;
	var <>refreshInterval = 0.05;
	var refreshLoopOn = false;
	var specs;
	
	*new {|virtualCont, name, origin|
		^super.new.init(virtualCont, name ? virtualCont.name)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argvirtualCont, argname|
		virtualCont = argvirtualCont;
		virtualCont.addDependant(this);
		name = argname;
	}
	
	makeWindow {|origin|
		var numSliders, presetMenu;
		numSliders = virtualCont.numFaders;
		window = SCWindow.new(name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		sliders = Array.newClear(numSliders);
		specs = Array.newClear(numSliders);
		virtualCont.getAllLabels.do({|label, i|
			var initVal, control, displaySpec;
			control = BMAbstractController.allControls[label.asSymbol];
			displaySpec = control.displaySpec;
			initVal = displaySpec.map(virtualCont.getFaderVal(i + 1));
			sliders[i] = EZSlider.new(window, 640@20, label.asString, \db,
				{|ez| var setVal;
					if(fromUpdate.not, {
						setVal = displaySpec.unmap(ez.value);
						virtualCont.setFaderVal(i + 1, setVal);
						setVal.postln;
					})
				}, initVal
			);
			sliders[i].numberView.background = Color.white.alpha_(0.4);
			specs[i] = displaySpec;
		
		});
		window.onClose = { virtualCont.removeDependant(this); onClose.value };
		window.front;
	}
	
	// could be some jitter, but safer
	startRefreshLoop {
		refreshLoopOn.not.if({
			refreshLoopOn = true;
			AppClock.sched(refreshInterval, {
				var resched;
				needsRefresh.if({resched = refreshInterval}, {refreshLoopOn = false});
				fromUpdate = true; // prevent a loop
				virtualCont.getAllFaders.do({|val, i| 
					sliders[i].value_(specs[i].map(val));
				});
				fromUpdate = false;
				needsRefresh = false;
				resched;
			});
		});
	}
	
	update {|changed, what, index, val|
		switch(what,
//			\faderVal, {
//				needsRefresh = true;
//				this.startRefreshLoop;
//			},
			\label, {sliders[index].labelView.string_(val.asString)}
		)
	}
	

}