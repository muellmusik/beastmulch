// sergio, this probably needs a cleanup method for the bus
BMMasterFader : BMAbstractAudioChainElement {

	var masterFaderSynth, <level, <>minLevel = -inf, <>maxLevel = 0, bus, <busIndex, <defaultLevel, <>acceptsMappings = true;

	*new { |target, addAction = \addToTail, name, defaultLevel = -12|
		^super.new.init(target, addAction, name, defaultLevel);
	}

	init {|argtarget, argaddAction, argname, argdefaultLevel|
		this.initNameAndTarget(argtarget, argaddAction, argname);
		bus = Bus.control(server, 1);
		busIndex = bus.index;
		defaultLevel = argdefaultLevel;
		this.level	= defaultLevel;
		this.addMasterFaderSynth;
	}

	level_ {| x |
	 	level = x.clip(minLevel, maxLevel);
	 	server.sendMsg("/c_set", busIndex, level.dbamp);
	 	this.changed(\level);
	}

	mappings {
		^IdentityDictionary[\level -> if(acceptsMappings, level, nil)]
	}

	mappings_ { | dict |
		var newLevel;
		dict = dict ? ();
		if(acceptsMappings, {
			newLevel = dict[\level] ? defaultLevel;
			this.level_(newLevel);
		}, {if(dict[\level].isNumber, {"Attempt to set mappings for BMMasterFader when acceptsMappings is false".warn})});
	}

	loadPiece {|pieceEvent|
		var level;
		level = pieceEvent.masterFaderLevel;
		this.level_(level ? defaultLevel);
	}

	// a little hacky but has worked ;-)
	addMasterFaderSynth {
		masterFaderSynth = {
			ReplaceOut.ar(0, In.ar(0, server.options.numOutputBusChannels) * In.kr(busIndex, 1));
		}.play(group, addAction: \addToTail);
	}

	free {
		group.release(BMOptions.crossfade);
		allChainElements[name] = nil;
		SystemClock.sched(BMOptions.crossfade, { group.free; bus.free; group = bus = nil;  });
	}

}

