// a simple controller that allows for onscreen guis.
// also allows for making user-level wrappers for arbitrary devices without subclassing
// 'native' values for this are 0-1

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
	
	setVal { |controlNum, val| 
		var chan;
		chan = controlNum - 1;
		server.sendMsg("/c_set", busIndex + chan, val);
		valueArray[chan] = val; 
		this.changed(\faderVal, chan, val);
	}
	
	getAllValues { ^valueArray; }
	
	setAllValues {|array|  array.do({|item, i| this.setVal(i + 1, item);}); }
	
	setLabel { |fader, name|
		labelArray[fader - 1] = name;
		this.changed(\label, fader - 1, name);
	}
	
	getLabel { |fader| ^labelArray[fader-1] }
	
	getAllLabels {  ^labelArray  }
	
	setAllLabels {|array| array.do({|item, i| this.setLabel(i+1, item);}); }
	
//	getInputArray {
//		^this.controlNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}
	
//	controlNames {^Array.fill(numControls, {|i| name.asString ++ "-" ++ (i+1)})}

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
		var numSliders, font, presetMenu, labelWidth;
		font = Font("Helvetica-Bold", 10);
		numSliders = virtualCont.numControls;
		window = SCWindow.new(name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		sliders = Array.newClear(numSliders);
		specs = Array.newClear(numSliders);
		labelWidth = virtualCont.controlNames.collect({|name| 
			name.asString.bounds(font).width
		}).maxItem;
		virtualCont.controlNames.do({|controlName, i|
			var initVal, control, label, displaySpec;
			label = virtualCont.getLabel(i + 1);
			if(label.size == 0, {label =  controlName.asString }); 
			control = BMAbstractController.allControls[controlName.asSymbol];
			displaySpec = control.displaySpec;
			initVal = displaySpec.map(virtualCont.getVal(i + 1));
			sliders[i] = EZSlider.new(window, 
				640@20, 
				label, 
				displaySpec,
				{|ez| var setVal;
					if(fromUpdate.not, {
						setVal = displaySpec.unmap(ez.value);
						virtualCont.setVal(i + 1, setVal);
						//setVal.postln;
					})
				}, initVal, labelWidth: labelWidth
			);
			sliders[i].numberView.background = Color.white.alpha_(0.4);
			sliders[i].font = font;
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
				virtualCont.getAllValues.do({|val, i| 
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
			\faderVal, {
				needsRefresh = true;
				this.startRefreshLoop;
			},
			\label, {sliders[index].labelView.string_(val.asString)}
		)
	}
	

}