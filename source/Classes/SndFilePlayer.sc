// has a maximum number of channels in order to allow it to return an input array and set the Bus
// start time currently broken

// currently will respond to any trigger message. Clean up later
SoundFilePlayer : BMAbstractAudioSource {
	var maxNumChannels, <latency, <>bus;
	var <buffer, <synth, <>releaseTime = 0.1, watcher, <rate = 1;
	var <sampleDur = 2.2675736961451e-05;
	
	*new {|maxNumChannels = 2, latency = 0.1, server, group|
		^super.new.init(maxNumChannels, latency, server ? Server.default, group);
	}
	
	init { |argMaxNumChannels, argLatency, argServer, argGroup|
		
		maxNumChannels = argMaxNumChannels;
		latency = argLatency;
		server = argServer;
		group = argGroup;
		bus = Bus.audio(server, maxNumChannels);
		if(group.isNil, {this.makeGroup});
		CmdPeriod.add(this);
		//allChainElements[name] = this;
	}
	
	read {|path, action|
		
		Routine.run {
			var condition, bundle;
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
		SynthDef(this.hash.asString, { arg out, gate = 1, rate = 1, loop = 0, updateRate = 0.1;
			var player;
			
			player = PlayBufSendIndex.ar(buffer.numChannels, buffer.bufnum,
				BufRateScale.kr(buffer.bufnum) * rate,1.0, 0.0, loop, updateRate.reciprocal, this.hash);
			FreeSelfWhenDone.kr(player);
			player = player * Linen.kr(gate, releaseTime: releaseTime, doneAction:2); 
			Out.ar(out, player); 
		}).send(server);
	}
	
	play { |startTime = 0, out|
		synth.isPlaying.not.if({
//			server.makeBundle(latency, {
				synth = Synth.head(group, this.hash.asString, 
					[\out, out ? bus.index, \rate, rate]);
				watcher = NodeWatcher.register(synth);
				synth.addDependant(this);
			//});
			this.changed(\play);
		});
		
	}
	
	stop { synth.isPlaying.if({synth.release; watcher.stop; synth = nil; this.changed(\stop);}) }
	
	//pause {} // maybe use run here
	
	free { this.stop;  server.makeBundle(releaseTime, {buffer.free;}); buffer = nil; 
		this.changed(\bufferFreed);
	} // free bus somewhere?
	
	// maybe a controller better?
	update {arg changed, what; 
		if(what == \n_end, {watcher.stop; synth = nil;});
		this.changed(what);
	}
	
//	getInputArray {|name = "Player"|
//		^InOutArray.fill(maxNumChannels, {|i| (name ++ (i + 1)).asSymbol -> (bus.index + i)});
//			
//	}
	
	asInOutArray {|name = "Player"|
		^InOutArray.fill(maxNumChannels, {|i| (name ++ (i + 1)).asSymbol -> (bus.index + i)});
	}
	
	path { ^buffer.notNil.if({buffer.path}, {nil}) }
	
	rate_ { |newRate| 
		rate = newRate;
		synth.set(\rate, rate, \loop, 0);
	}
	
	makeGroup {
		group = Group.head(server);
	}
	
	cmdPeriod { this.makeGroup }
	
	name { ^name ? "Soundfile Player" }
	
	gui { ^SoundFilePlayerGUI(this) }
}


// stopwatch should probably be in player
SoundFilePlayerGUI : BMAbstractGUI {
	
	var player, responder, clockView, loadButton, info, dur, playButton, stopButton, clust, clearButton;
	
	*new { |player, name|
		^super.new.init(player).makeWindow;
	}
	
	init { |argplayer, argname|
		player = argplayer;
		name = argname ? "Sound File Player";
		OSCresponderNode(player.server.addr,'/tr',{ arg time,responder,msg;
			this.updateTimeDisplay(msg.last * player.sampleDur);
		}).add;
		player.addDependant(this);
	}
	
	makeWindow {
		window = SCWindow.new(name, Rect(220, 700, 650, 100), false)
			.userCanClose = false;
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		clockView = SCStaticText.new(window, Rect(10,10,200,40));
		clockView.string = "00:00:00.0";
		//clockView.background = Color.black;
		clockView.background = HiliteGradient(Color.black.alpha_(0.1), Color.black, \v, 256, 0.5);
		clockView.font = Font("Helvetica-Bold", 18);
		//clockView.stringColor = Color.yellow(0.9);
		clockView.stringColor = Color.new255(106, 90, 205);
		clockView.align = \center;
		clust = SCVLayoutView(window,Rect(10,10,200,40));
		info = SCStaticText.new(clust, Rect(10,10,200,20));
		dur = SCStaticText.new(window, Rect(10,10,150,20)).align_(\center);
		loadButton = SCButton.new(clust, Rect(10,10,200,20));
		loadButton.states = [["Load File", Color.black,Color.clear]];
		loadButton.action = {
			var oldString;
			oldString = info.string;
			CocoaDialog.getPaths({ arg paths; 
				player.read(paths[0]);
			}, {{info.string = oldString}.defer});
		};
		window.view.decorator.nextLine;
		playButton = SCButton.new(window, Rect(10,10,200,20));
		playButton.states = [["Play", Color.black,Color.clear]];
		playButton.action = { player.play; }; // stopwatch started by dependancy
		//playButton.action = { player.play(0, 0); stopwatch.start; };
		stopButton = SCButton.new(window, Rect(10,10,200,20));
		stopButton.states = [["Stop", Color.black,Color.clear]];
		stopButton.action = { player.stop; }; // stopwatch stopped by dependancy
		clearButton = SCButton.new(window, Rect(10,10,200,20));
		clearButton.states = [["Free Buffer", Color.black,Color.clear]];
		clearButton.action = { player.stop; player.free; }; // stopwatch stopped by dependancy
		window.front;
	}
	
	updateTimeDisplay {|time|
		var string, minutes, hours, seconds;
		minutes = (time/60).trunc(1);
		if(minutes >= 60,{ hours = (minutes/60).trunc(1);
			minutes = minutes%60;
		},{
			hours = 0;
		});
		seconds = (time%60).trunc(0.1);
		
		if(hours == 0, {string = "00:"}, {string = hours.asString ++ ":" });
		if(minutes < 10, {string = string ++ "0" ++ minutes ++ ":"}, 
			{string = string ++ minutes ++ ":"; });
		if(seconds<10,{string = string ++ "0" ++ seconds},
			{string = string ++ seconds});
		if(string.size < 10, {string = string ++ ".0"});
		{ clockView.string = string;}.defer;
		this.changed(\time, string);
	}
	
	// always updated from player
	update {arg changed, what; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
			\n_end, {this.updateTimeDisplay(0)},
//			\play, {stopwatch.start;},
			\bufferFreed, {info.string = ""; dur.string = "";},
			\stop, {this.updateTimeDisplay(0)},
			\loading, {info.string = "Loading...";},
			\loaded, {{info.string = player.path.basename; dur.string =  "Length:" + (player.buffer.numFrames / player.buffer.sampleRate).asTimeString}.defer }
		)
	}
	
	changed { arg what ... moreArgs;
		dependantsDictionary.at(this).do({ arg item;
			item.update(this, what, *moreArgs);
		});
	}
}

