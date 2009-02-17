// has a maximum number of channels in order to allow it to return an input array and set the Bus

// start time only works when not already playing. Make sense?

// update for rate FF FB?

BMSoundFilePlayer : BMAbstractAudioSource {
	
	var maxNumChannels, <latency, <>bus;
	var <buffer, <synth, <>releaseTime = 0.1, watcher, <rate = 1;
	var <sampleDur = 2.2675736961451e-05;
	var blockPlay = false;
	var resp, trigID;
	
	*new {|maxNumChannels = 2, latency = 0.1, group, server, name|
		^super.new.init(maxNumChannels, latency, group, server ? Server.default, name);
	}
	
	init { |argMaxNumChannels, argLatency, argGroup, argServer, argName|
		
		maxNumChannels = argMaxNumChannels;
		latency = argLatency;
		server = argServer;
		group = argGroup;
		name = argName ? this.makeName;
		bus = Bus.audio(server, maxNumChannels);
		if(group.isNil, {this.makeGroup});
		//CmdPeriod.add(this);
		allChainElements[name] = this;
		BMTimeSources.addReference(this);
		// we check by node ID but this should be good enough to avoid conflicts with others
		// if they don't
		trigID = this.hash & 65535; // need 16 bit
	}
	
	startListening {
		resp = OSCresponderNode(this.server.addr,'/tr',{ arg time,responder,msg;
			if(msg[1] == synth.nodeID, {
				this.changed(\time, msg.last * this.sampleDur, rate, time);
			});
		}).add;
	}
	
	read {|path, action|
		
		Routine.run {
			var condition, bundle;
			this.dependants.postln;
			this.changed(\loading);
			
			// create a condition variable to control execution of the Routine
			condition = Condition.new;
			this.stop;
			releaseTime.wait;
			
			bundle = server.makeBundle(false, { buffer.free });
			server.sync(condition, bundle);
			"Old Buffer Freed".postln;
			buffer = Buffer.read(server, path, action: {(path + "loaded").postln;
			sampleDur = buffer.sampleRate.reciprocal;
			this.changed(\loaded);
			this.changed(\base);
			this.sendDef; action.value });
		};

		
		
//		var oldBuffer;
//		oldBuffer = buffer;
//		this.stop;
//		buffer = Buffer.read(server, path, action: {(path + "loaded").postln;
//			this.sendDef; action.value });
//		server.makeBundle(releaseTime, {oldBuffer.free});
	}
	
//	sendDef { // only called after Buffer vars updated
//		SynthDef(this.hash.asString, { arg out, gate = 1, rate = 1, loop = 0, updateRate = 1;
//			var player, phasor;
////			phasor = Phasor.ar(0, BufRateScale.kr(buffer.bufnum) * rate, 
////				0, BufFrames.kr(buffer.bufnum) * 1.1);
////			phasor = Phasor.ar(0, BufRateScale.kr(buffer.bufnum) * rate, 
////				0, buffer.numFrames);
//			//phasor = LFSaw.ar(BufDur.ir(buffer.bufnum).reciprocal * rate, add: 1).range(0, BufFrames.ir(buffer.bufnum) + 1);
//			phasor = LFSaw.ar(BufDur.ir(buffer.bufnum).reciprocal * rate, 1).range(0, BufFrames.ir(buffer.bufnum));
//			//phasor = Line.ar(0, buffer.numFrames, BufDur.ir(buffer.bufnum));
//			SendTrig.kr(Impulse.kr(updateRate.reciprocal), this.hash, phasor);
//			//player =	BufRd.ar(buffer.numChannels, buffer.bufnum, phasor, loop, 4); 
//			//player =	BufRd.ar(buffer.numChannels, buffer.bufnum, phasor, loop, 1); 
//			player = PlayBuf.ar(buffer.numChannels, buffer.bufnum,
//				BufRateScale.kr(buffer.bufnum),1.0);
//			FreeSelfWhenDone.kr(player);//(BufFrames.kr(buffer.bufnum) * 0.5));
//			player = player * Linen.kr(gate, releaseTime: releaseTime, doneAction:2); 
//			Out.ar(out, player); 
//		}).send(server);
//	}

	sendDef { // only called after Buffer vars updated
		SynthDef(this.hash.asString, { arg out, gate = 1, rate = 1, loop = 0, updateRate = 0.1, 
				startPos = 0, t_trig = 1;
			var player, freeEnv, pauseEnv;
			
			player = PlayBufSendIndex.ar(buffer.numChannels, buffer.bufnum,
				BufRateScale.kr(buffer.bufnum) * rate,t_trig, startPos, loop, 
				updateRate.reciprocal, 
				trigID);
			FreeSelfWhenDone.kr(player);
			freeEnv = Linen.kr(gate, 0, releaseTime: releaseTime, doneAction:2);
			// avoid DC offset by fading in and out
			pauseEnv = Linen.kr(rate, releaseTime: 0.01, doneAction:0);
			player = player * freeEnv * pauseEnv; 
			Out.ar(out, player); 
		}).send(server);
	}
	
	// startTime only works if we're not already playing
	play { |startTime = 0, out|
		this.rate_(1.0);
		(synth.isPlaying.not && blockPlay.not && synth.isNil && buffer.notNil).if({
			this.startListening;
			blockPlay = true;
//			server.makeBundle(latency, {
				synth = Synth.head(group, this.hash.asString, 
					[\out, out ? bus, \rate, rate, 
					\startPos, startTime * buffer.sampleRate]);
				watcher = NodeWatcher.register(synth);
				synth.addDependant(this);
			//});
			
			SystemClock.sched(0.1, {blockPlay = false;});
		}, {buffer.isNil.if({this.changed(\playFailed); ^this})});
		this.changed(\play);
	}
	
	stop { 
		synth.isPlaying.if({ this.stopCleanUp }); 
	}
	
	togglePlay {
		synth.isPlaying.if({ if(rate != 0, {this.pause}, {this.play}) }, {this.play });
	}
	
	stopCleanUp {
		resp.remove;
		blockPlay = true;
		watcher.stop;
		watcher = nil;
		synth.isPlaying.if({synth.release; }); 
		synth = nil; 
		rate = 1; 
		this.changed(\stop); 
		this.changed(\time, 0, 0, Main.elapsedTime); // not sure about this
		SystemClock.sched(0.1, {blockPlay = false;});
	}
	
	pause { synth.isNil.not.if({ this.rate = 0; this.changed(\pause);}) } // this will continue to ping time vals
	
	free { this.stop;  server.makeBundle(releaseTime, {buffer.free;}); buffer = nil;
		this.changed(\bufferFreed);
	} // free bus somewhere? remove from allPlayers list?
	
	// maybe a controller better?
	update {arg changed, what; 
		if(what == \n_end, {this.stopCleanUp});
		this.changed(what);
	}
	
	setTime {|time|
		synth.isPlaying.if({
			synth.set(\startPos, time * buffer.sampleRate, \t_trig, 1);
		}, {
			// start and pause
			(buffer.notNil && (time != 0)).if({
				this.play(time);
				this.pause;
			});
		});
	}
	
//	getInputArray {|name = "Player"|
//		^BMInOutArray.fill(maxNumChannels, {|i| (name ++ (i + 1)).asSymbol -> (bus.index + i)});
//			
//	}
	
	asBMInOutArray {
		^BMInOutArray.fill(maxNumChannels, {|i| (name.asString + (i + 1)).asSymbol -> (bus.index + i)});
	}
	
	path { ^buffer.notNil.if({buffer.path}, {nil}) }
	
	rate_ { |newRate| 
		rate = newRate;
		synth.set(\rate, rate, \loop, 0);
	}
	
//	makeGroup {
//		group = Group.head(server);
//	}
	
//	cmdPeriod { blockPlay = false; this.makeGroup }
	
	gui { ^BMSoundFilePlayerGUI(this, this.name) }
	
  
}


BMSoundFilePlayerGUI : BMAbstractGUI {
	
	var player, responder, clockView, loadButton, info, dur, playButton, stopButton, clust, clust2; 	var clearButton, forwButton, backButton, clockButton, bigClock, bigText;
	
	*new { |player, name|
		^super.new.init(player).makeWindow;
	}
	
	init { |argplayer, argname|
		player = argplayer;
		name = argname ? "Sound File Player";
		player.addDependant(this);
	}
	
	makeWindow {
		window = SCWindow.new(name, Rect(220, 700, 640, 100), false);
		window.view.background_(Color.white.alpha_(0.2));
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		window.view.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			if(unicode == 32, {player.togglePlay});
			if(unicode == 13, {player.stop});
		};
		
		clockView = SCStaticText.new(window, Rect(0,0,200,45));
		clockView.string = "00:00:00.0";
		//clockView.background = Color.black;
		clockView.background = HiliteGradient(Color.black.alpha_(0.1), Color.black, \v, 256, 0.5);
		clockView.font = Font("Helvetica-Bold", 18);
		//clockView.stringColor = Color.yellow(0.9);
		clockView.stringColor = Color.new255(106, 90, 205);
		clockView.align = \center;
		
		window.view.decorator.shift(-40, 10);
		clockButton = RoundButton.new(window, Rect(0,0,25,25)).extrude_( false )
			.canFocus_(false).radius_( 3 );
		clockButton.states = [[\clock, Color.white, Color.white.alpha_(0.2)]];
		clockButton.action = {this.makeBigClock};
		
		window.view.decorator.shift(5, -10);
		
		clust = SCVLayoutView(window,Rect(10,10,200,40));
		clust2 = SCVLayoutView(window,Rect(10,10,200,40));
	     info = SCStaticText.new(clust, Rect(10,10,150,20));
	     info.font = Font("Helvetica-Bold", 12);
		dur = SCStaticText.new(clust2, Rect(10,10,150,20));
		dur.font = Font("Helvetica-Bold", 12);
		player.path.notNil.if({{
			info.string = player.path.basename; 
			dur.string =  "Length:" + 
				(player.buffer.numFrames / player.buffer.sampleRate).asTimeString
			}.defer });
		
//		loadButton = SCButton.new(clust, Rect(10,10,200,20));
//		loadButton.states = [["Load File", Color.black,Color.clear]];
		loadButton = RoundButton.new(clust, Rect(10,10,200,20)).extrude_(false).canFocus_(false);
		loadButton.states = [[\folder, Color.black,Color.clear]];
		loadButton.action = {
			var oldString;
			oldString = info.string;
			CocoaDialog.getPaths({ arg paths; 
				player.read(paths[0]);
			}, {oldString.notNil.if({{info.string = oldString}.defer})});
		};
		clearButton = RoundButton.new(clust2, Rect(10,10,200,20)).extrude_(false).canFocus_(false);
		clearButton.states = [[\x, Color.black,Color.clear]];
		clearButton.action = { player.stop; player.free; }; // stopwatch stopped by dependancy
		
		window.view.decorator.nextLine;
		
		stopButton = RoundButton.new(window, Rect(10,10,200,20)).extrude_(false).canFocus_(false);
		stopButton.states = [[\stop]];
		stopButton.action = { player.stop; }; // stopwatch stopped by dependancy
		
		backButton = RoundButton.new(window, Rect(10,10,95,20)).extrude_(false).canFocus_(false);
		backButton.states = [[\rewind]];
		backButton.action = { player.rate = -6; playButton.value = 0 };
		
//		playButton = SCButton.new(window, Rect(10,10,200,20));
//		playButton.states = [["Play", Color.black,Color.clear]];
		playButton = RoundButton.new(window, Rect(10,10,200,20)).extrude_(false).canFocus_(false);
		playButton.states = [[\play], [\pause]];
		playButton.action = { |butt|
			switch (butt.value,
				1, {player.play;},
				0, {player.pause}
			)
			
		}; // stopwatch started by dependancy
		//playButton.action = { player.play(0, 0); stopwatch.start; };
		
		
		forwButton = RoundButton.new(window, Rect(10,10,95,20)).extrude_( false ).canFocus_(false);
		forwButton.states = [[\forward]];
		forwButton.action = { player.rate = 6; playButton.value = 0 };
		
		window.onClose_({
			player.removeDependant(this); 
			bigClock.notNil.if({bigClock.close});
			onClose.value(this);
		});
		window.front;
	}
	
	makeBigClock {
		bigClock.isNil.if({
			bigClock = SCWindow.new("Big Clock", Rect(600, 800, 800, 180)).alwaysOnTop_(true);
			bigClock.alpha = 0.95;
			bigClock.onClose = { bigClock = nil; };
			bigText = SCStaticText.new(bigClock, Rect(0, 0, 800, 180)).resize_(5);
			bigText.string = "00:00:00.0";
			//clockView.background = Color.black;
			bigText.background = HiliteGradient(Color.black.alpha_(0.3), Color.black, \v, 1024, 0.5);
			bigText.font = Font("Helvetica-Bold", 120);
			bigText.stringColor = Color.new255(106, 90, 205);
			bigText.align = \center;
			bigClock.front;
		});
	}
    
    	updateTimeDisplay {| string |
		{ clockView.string = string; bigClock.notNil.if({bigText.string = string});}.defer;
	}
	
	// always updated from player
	update {arg changed, what ...args; 
		
		{
		switch(what,
			\n_end, {this.updateTimeDisplay(0.getTimeString);
				{playButton.value = 0;}.defer;
			},
			\play, {
				playButton.value = 1;
			},
			\pause, {
				playButton.value = 0;
			},
			\playFailed, {
				//"Playing failed".postln; 
				this.updateTimeDisplay(0.getTimeString);
				playButton.value = 0;
				},
			\bufferFreed, {info.string = ""; dur.string = "";},
			\stop, { 
				this.updateTimeDisplay(0.getTimeString);
				playButton.value = 0;
				},
			\loading, {info.string = "Loading...";},
			\loaded, {info.string = player.path.basename; dur.string =  "Length:" + (player.buffer.numFrames / player.buffer.sampleRate).asTimeString },
			\time, { this.updateTimeDisplay(args.first.getTimeString) }
		)
		}.defer
	}
	
	changed { arg what ... moreArgs;
		dependantsDictionary.at(this).do({ arg item;
			item.update(this, what, *moreArgs);
		});
	}
}

