// a simple controller that allows for onscreen guis.
// also allows for making user-level wrappers for arbitrary devices without subclassing
// 'native' values for this are 0-1

BMVirtualController : BMAbstractController {

	*new { |name, server, numFaders = 8|
		^super.new.init(name, server ? Server.default, numFaders);
	}
	
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
	
	getInputArray {
		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, InOutArray);
	}
	
	faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}
}

// simple onscreen slider GUI for a BMVirtualController
BMVirtualControllerSliders : BMAbstractGUI {
	var virtualCont, sliders, fromUpdate = false;
	
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
		virtualCont.getAllLabels.do({|label, i|
			var initVal;
			initVal = virtualCont.getFaderVal(i + 1).ampdb;
			sliders[i] = EZSlider.new(window, 640@20, label.asString, \db,
				{|ez| var setVal;
					if(fromUpdate.not, {
						setVal = ez.value.dbamp;
						virtualCont.setFaderVal(i + 1, setVal);
					})
				}, initVal
			);
			sliders[i].numberView.boxColor = Color.white.alpha_(0.4);
		
		});
		window.front;
	}
	
	update {|changed, what, index, val|
		switch(what,
			\faderVal, {
				fromUpdate = true;
				sliders[index].value_(val.ampdb);
				fromUpdate = false;
				},
			\label, {sliders[index].labelView.string_(val.asString)}
		)
	}
	

}