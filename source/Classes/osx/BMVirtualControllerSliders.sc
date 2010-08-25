BMAbstractVirtualControllerGUI : BMAbstractGUI {
	var virtualCont, guiCtrls;
	var needsRefresh = false;
	var <>refreshInterval = 0.016666666666667;
	var refreshLoopOn = false;
	var displaySpecs;
	
	*new {|virtualCont, name, origin|
		^super.new.init(virtualCont, name ? virtualCont.name)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argvirtualCont, argname|
		virtualCont = argvirtualCont;
		virtualCont.addDependant(this);
		name = argname;
	}
	
	// could be some jitter, but safer
	startRefreshLoop {
		refreshLoopOn.not.if({
			refreshLoopOn = true;
			AppClock.sched(refreshInterval, {
				var resched;
				needsRefresh.if({resched = refreshInterval}, {refreshLoopOn = false});
				virtualCont.getAllValues.do({|val, i| 
					guiCtrls[i].value_(displaySpecs[i].map(val));
				});
				needsRefresh = false;
				resched;
			});
		});
	}
	
	update {|changed, what, index, val|
		switch(what,
			\controlVal, {
				needsRefresh = true;
				this.startRefreshLoop;
			},
			\label, {guiCtrls[index].labelView.string_(val.asString)}
		)
	}
	

}

// simple onscreen slider GUI for a BMVirtualController
BMVirtualControllerSliders : BMAbstractVirtualControllerGUI {
	
	makeWindow {|origin|
		var numSliders, font, presetMenu, labelWidth;
		font = Font("Helvetica-Bold", 10);
		numSliders = virtualCont.numControls;
		window = SCWindow.new(name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		guiCtrls = Array.newClear(numSliders);
		displaySpecs = Array.newClear(numSliders);
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
			guiCtrls[i] = EZSlider.new(window, 
				640@20, 
				label, 
				displaySpec,
				{|ez| var setVal;
					setVal = control.controlSpec.map(displaySpec.unmap(ez.value));
					virtualCont.setVal(i + 1, setVal);
				}, initVal, labelWidth: labelWidth
			);
			guiCtrls[i].numberView.background = Color.white.alpha_(0.4);
			guiCtrls[i].font = font;
			displaySpecs[i] = displaySpec;
		
		});
		window.onClose = { virtualCont.removeDependant(this); onClose.value };
		window.front;
	}
	
}

BMPluginSliderGUI : BMAbstractVirtualControllerGUI {
	
	makeWindow {|origin|
		var numSliders, plugin, spec, specsDict, font, presetMenu, labelWidth;
		plugin = virtualCont.plugin;
		spec = plugin.spec;
		specsDict = plugin.specsDict;
		font = Font("Helvetica-Bold", 10);
		numSliders = specsDict.size;
		window = SCWindow.new(name, 
			Rect(300, 300, 652, (numSliders + 1) * 24), false); // 508
		window.view.decorator = FlowLayout(window.view.bounds);
		window.view.background = Color.rand.alpha_(0.3);
		guiCtrls = Array.newClear(numSliders);
		displaySpecs = Array.newClear(numSliders);
		labelWidth = virtualCont.controlNames.collect({|name| 
			name.asString.bounds(font).width
		}).maxItem;
		specsDict.sortedKeysValuesDo({|controlName, cspec, i|
			var initVal, control, label, displaySpec;
			label = virtualCont.getLabel(i + 1);
			if(label.size == 0, {label =  controlName.asString }); 
			control = BMAbstractController.allControls[controlName.asSymbol];
			displaySpec = control.displaySpec;
			initVal = plugin.get(controlName);
			guiCtrls[i] = EZSlider.new(window, 
				640@20, 
				label, 
				displaySpec,
				{|ez| var setVal;
					setVal = control.controlSpec.map(displaySpec.unmap(ez.value));
					virtualCont.setVal(i + 1, setVal);
				}, initVal, labelWidth: labelWidth
			);
			guiCtrls[i].numberView.background = Color.white.alpha_(0.4);
			guiCtrls[i].font = font;
			displaySpecs[i] = displaySpec;
		
		}, {|a, b|
			var argArray;
			argArray = plugin.spec.ugenGraphFunc.def.argNames;
			argArray.indexOf(a) < argArray.indexOf(b)
		});
		window.view.decorator.nextLine.shift(10, 10);
		presetMenu = SCPopUpMenu(window, Rect(0, 0, 100, 20));
		presetMenu.items = ["presets", "-"] ++ spec.presets.keys;
		presetMenu.action = {
			if(presetMenu.value > 1, {
				plugin.preset_(presetMenu.items[presetMenu.value].asSymbol);
				guiCtrls.keysValuesDo({|key, slid| 
					var newVal;
					newVal = plugin.get(key);
					(slid.controlSpec.units == " dB" 
						&& plugin.attributes[\usesLinearAmp]).if({ 
						newVal = newVal.ampdb;
					});
					slid.value = newVal;
				});
			});
		};
		window.onClose = { virtualCont.removeDependant(this); onClose.value };
		window.front;
	}
	
}