// this probably needs a cleanup method for the bus
BMMasterFader : BMAbstractAudioChainElement {

	
	var masterFaderSynth, <level = -12, <>minLevel = -inf, <>maxLevel = 0, bus, <busIndex;
	
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
	 	this.changed(\level);
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
			ReplaceOut.ar(0, In.ar(0, server.options.numOutputBusChannels) * In.kr(busIndex, 1));
		}.play(group, addAction: \addToTail);
	}
		
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
		allChainElements[name] = nil;
		SystemClock.sched(BMOptions.crossfade, { group.free; bus.free; group = bus = nil;  });
		//CmdPeriod.remove(this)
	}
	
}

