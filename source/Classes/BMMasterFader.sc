BMMasterFader : BMAbstractAudioChainElement {

	
	var masterFaderSynth, <level = -12, <minLevel = -92, <maxLevel = 6, <busIndex;
	
	*new {| group, server, name |
		 
		 ^super.new.init(group, server ? Server.default, name ? this.name);

	}
	
	init {| arggroup, argserver, argname |

		  group	= arggroup;
		  server	= argserver;
		  name	= argname;
		  if(group.isNil, {this.makeGroup});
		  allChainElements[name] = this;
		  busIndex = BMAbstractController.allControllers[\etherSense].busIndex 
				    +
				    BMAbstractController.allControllers[\etherSense].numFaders;
		  this.level	= level;
		  this.addMasterFaderSynth;
		  CmdPeriod.add(this);

	}
	
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		
		^this.new(group, server, name)
	
	}

	
	level_ {| x |

	 	level = x;
	 	server.sendMsg("/c_set", busIndex, level.postln.dbamp);
	
	}

	
	mappings { 
		^IdentityDictionary[\level -> level]
	}
	
	mappings_ { | dict |
	 
		level = dict[\level];
	
	}
	
	// a little hacky but has worked ;-)
	addMasterFaderSynth {

		masterFaderSynth = {
			ReplaceOut.ar(0, In.ar(0, BMOptions.numOutputBusChannels) * In.kr(busIndex, 1)
			);
		}.play(group, addAction: \addToTail);

	}
	
	gui { ^BMMasterFaderGUI(this) } 
	
	cmdPeriod { 

		server.makeBundle(nil, { 
			server.sync;
			this.makeGroup;
			server.sync;
			this.addMasterFaderSynth;
			server.sync
		})
	
	} 
	
	makeGroup { group = Group.tail(server) }
	
}


BMMasterFaderGUI : BMAbstractGUI {
	var masterFader;
	
	*new {| masterFader, name |
		^super.new.init(masterFader, name ? masterFader.name)
			 .makeWindow;
	}
	
	init {| argMasterFader, argName |
		 masterFader 	= argMasterFader;
		 name 		= argName;

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
					.value_(masterFader.level.linlin(masterFader.minLevel, masterFader.maxLevel, 0.0, 1.0))
					.action_({| view | 
							 masterFader.level	= view.value.linlin(0.0, 1.0, masterFader.minLevel, masterFader.maxLevel);
							 numberBox.value 	= masterFader.level.round(1)
				     });
				   
		numberBox	= ScrollingNBox(window, window.view.bounds
					.resizeTo(90, 60)
					.moveBy(15, window.view.bounds.height - 80))
					.value_(masterFader.level)
					.font_(Font( "Monaco", 35 ))
					.action_({| view | 
							 view.value = view.value.clip(masterFader.minLevel, masterFader.maxLevel);
							 masterFader.level = view.value;
							 slider.value = view.value.linlin(masterFader.minLevel, masterFader.maxLevel, 0.0, 1.0)
					});
		
		window.onClose = { onClose.value(this) }
		
	}
}
