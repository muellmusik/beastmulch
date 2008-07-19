SoundFileMultiStreamer {
	var maxNumChannels, <latency, <server, <>bus;
	var <buffer, <synth, <>releaseTime = 0.1, watcher, <rate = 1;
	var <sampleDur = 2.2675736961451e-05;
	var <pathlist;
	
	*new {|maxNumChannels = 2, latency = 0.1, server|
		^super.newCopyArgs(maxNumChannels, latency, server ? Server.default).init;
	}
	
	init {
		bus = Bus.audio(server, maxNumChannels);
	}
	
	read {|paths, action|
		pathlist = paths;
		Routine.run {
			var condition, bundle;
			this.changed(\loading);
			// create a condition variable to control execution of the Routine
			condition = Condition.new;
			this.stop;
			releaseTime.wait;
			
			bundle = server.makeBundle(false, { buffer.do({|buf| buf.free}) });
			server.sync(condition, bundle);
			"Old Buffer Freed".postln;
			bundle = server.makeBundle(false, {
				buffer = Buffer.allocConsecutive(paths.size, server, 32768, 1);
			});
			server.sync(condition, bundle);
			bundle = server.makeBundle(false, {
				paths.do({|path, i| buffer[i].cueSoundFile(path);});
			});
			server.sync(condition, bundle);
			"Files Loaded".postln;
			sampleDur = buffer[0].sampleRate.reciprocal;
			this.changed(\loaded);
			this.sendDef; 
			action.value
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
			
			player = Array.fill(buffer.size, { |i|
				DiskIn.ar(1, buffer[i].bufnum);
			});

			player = player * Linen.kr(gate, releaseTime: releaseTime, doneAction:2); 
			Out.ar(out, player); 
		}).send(server);
	}
	
	play { |startTime = 0, out|
		synth.isPlaying.not.if({
//			server.makeBundle(latency, {
				synth = Synth.head(server, this.hash.asString, 
					[\out, out ? bus.index]);
				watcher = NodeWatcher.register(synth);
				synth.addDependant(this);
			//});
			this.changed(\play);
		});
		
	}
	
	stop { 
		synth.isPlaying.if({
			Routine.run {
				var condition, bundle;
				condition = Condition.new;
				synth.release;
				releaseTime.wait;
				watcher.stop; synth = nil; this.changed(\stop);
				
				bundle = server.makeBundle(false, { buffer.do(_.close);});
				server.sync(condition, bundle);
				"Closed".postln;
				bundle = server.makeBundle(false, { 
					pathlist.do({|path, i| buffer[i].cueSoundFile(path);});
				});
				server.sync(condition, bundle);
				"Recued".postln;
			};
		});
	}
	//pause {} // maybe use run here
	
	free { this.stop;  server.makeBundle(releaseTime, { buffer.do(_.free) }); buffer = nil; 
		this.changed(\bufferFreed);
	} // free bus somewhere?
	
	// maybe a controller better?
	update {arg changed, what; 
		if(what == \n_end, {watcher.stop; synth = nil;});
		this.changed(what);
	}
	
	getInputArray {|name = "Player"|
		^InputArray.fill(maxNumChannels, {|i| (name ++ (i + 1)).asSymbol -> (bus.index + i)});
	}
	
	path { ^buffer.notNil.if({buffer.collect(_.path)}, {nil}) }
	
	rate_ { |newRate| 
		rate = newRate;
		synth.set(\rate, rate, \loop, 0);
	}
}

BMSoundFileMultiStreamerGUI : BMSoundFilePlayerGUI {
	
	init {
//		OSCresponderNode(player.server.addr,'/tr',{ arg time,responder,msg;
//			this.updateTimeDisplay(msg.last * player.sampleDur);
//		}).add;
		player.addDependant(this);
	}
	
		makeWindow {
		window = SCWindow.new("Sound File Multi Streamer", Rect(220, 700, 650, 100), false)
			.userCanClose = false;
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		clockView = SCStaticText.new(window, Rect(10,10,200,40));
		clockView.string = "No Time like the Present";
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
				player.read(paths);
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
	
		// always updated from player
	update {arg changed, what; 
		//if(what == \n_end, {stopwatch.stop;});
		switch(what,
//			\play, {stopwatch.start;},
			\bufferFreed, {info.string = ""; dur.string = "";},
			\loading, {info.string = "Loading...";},
			\loaded, {{info.string = player.pathlist.collect(_.basename).asString; dur.string =  "Length:" + (player.buffer.collect(_.numFrames).maxItem.postln * player.sampleDur).asTimeString}.defer }
		)
	}
	
	changed { arg what ... moreArgs;
		dependantsDictionary.at(this).do({ arg item;
			item.update(this, what, *moreArgs);
		});
	}

}