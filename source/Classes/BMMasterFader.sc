// this probably needs a cleanup method for the bus
BMMasterFader : BMAbstractAudioChainElement {

	
	var masterFaderSynth, <level = -12, <minLevel = -inf, <maxLevel = 0, bus, <busIndex;
	
//use super
//	*new {| group, server, name |
//		 ^super.new.init(group, server ? Server.default, name);
//	}

	*new { |target, addAction = \addToTail, name| 
		^super.new.init(target, addAction, name);
	}
	
	init {|argtarget, argaddAction, argname|
		this.initNameAndTarget(argtarget, argaddAction, argname);
		bus = Bus.control(server, 1);
		busIndex = bus.index;
		this.level	= level;
		this.addMasterFaderSynth;
	}
	
//	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
//		^this.new(group, server, name)
//	}

	level_ {| x |
	 	level = x.clip(minLevel, maxLevel);
	 	server.sendMsg("/c_set", busIndex, level.dbamp);
	}

	mappings { 
		^IdentityDictionary[\level -> level]
	}
	
	mappings_ { | dict |
		dict = dict ? ();
		level = dict[\level] ? -12;
	}
	
	// a little hacky but has worked ;-)
	addMasterFaderSynth {
		masterFaderSynth = {
			ReplaceOut.ar(0, In.ar(0, BMOptions.numOutputBusChannels) * In.kr(busIndex, 1));
		}.play(group, addAction: \addToTail);
	}
	
	gui { ^BMMasterFaderGUI(this) } 
	
//	cmdPeriod { 
//
//		server.makeBundle(nil, { 
//			server.sync;
//			this.makeGroup;
//			server.sync;
//			this.addMasterFaderSynth;
//			server.sync
//		})
//	
//	} 
	
//	makeGroup { group = Group.tail(server) }
	
	free { 
		group.release(BMOptions.crossfade);
		SystemClock.sched(BMOptions.crossfade, { group.free; bus.free; group = bus = nil;  });
		CmdPeriod.remove(this)
	}
	
}


BMMasterFaderGUI : BMAbstractGUI {
	var masterFader, spec;
	
	*new {| masterFader, name |
		^super.new.init(masterFader, name ? masterFader.name)
			 .makeWindow;
	}
	
	init {| argMasterFader, argName |
		 masterFader 	= argMasterFader;
		 name 		= argName;
		 masterFader.addDependant(this);
		 spec = \db.asSpec;
	}
	
	makeWindow {
		
		var slider, numberBox;

		window 	= SCWindow("MF", 
						  Rect.new( 
						  	SCWindow.screenBounds.width - 120, 0, 120, 
						  	SCWindow.screenBounds.height - 150
						  ), 
						  false
				   ).front;
		
		slider 	= SmoothSlider(window, window.view.bounds.resizeBy(-30, -100).moveBy(15, 15))
					.mode_(\move).canFocus_(false)
					.value_(spec.unmap(masterFader.level))					.action_({| view | 
							 masterFader.level	= spec.map(view.value);
							 numberBox.value 	= masterFader.level.round(0.1)
				     });
				   
		numberBox	= NumberBox(window, window.view.bounds
					.resizeTo(90, 60)
					.moveBy(15, window.view.bounds.height - 80))
					.value_(masterFader.level.round(0.1))
					.font_(Font( "Monaco", 22 ))
					.align_(\center)
					.action_({| view | 
							 masterFader.level = view.value;
							 view.value = masterFader.level.round(0.1);
							 slider.value = spec.unmap(masterFader.level);
					});
		
		window.onClose = { masterFader.removeDependant(this);
						onClose.value(this) 
					    }
		
	}
}
